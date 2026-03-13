package ootkhuimunpnu.vntm.search;

import ootkhuimunpnu.vntm.monitoring.PerformanceProfiler;
import ootkhuimunpnu.vntm.monitoring.StatisticsCollector;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.join.IJoinStrategy;
import ootkhuimunpnu.vntm.pruning.IPruningStrategy;
import ootkhuimunpnu.vntm.topk.ITopKManager;
import ootkhuimunpnu.vntm.utility.PULList;

import java.util.*;

/**
 * Best-First Search (BeFS) implementation of the itemset search strategy.
 *
 * <h2>Core Idea</h2>
 * Unlike depth-first search (DFS) which commits to a subtree before
 * considering alternatives, Best-First Search always expands the
 * <em>most promising</em> open node first.  Promising-ness is measured by
 * the <b>probabilistic max-potential</b> of a utility list:
 *
 * <pre>
 *   maxPotential(X) = EU(X) + ΣR(X)
 * </pre>
 *
 * where {@code EU(X)} is the expected utility and {@code ΣR(X)} is the
 * expected remaining utility (an upper bound on how much any extension of
 * X can contribute).  This is the tightest polynomial-time upper bound on
 * the expected utility of any proper super-itemset of X.
 *
 * <h2>Search Process</h2>
 * <ol>
 *   <li>All valid 1-itemsets (extensions of the root prefix) are pushed into
 *       a max-heap ({@link PriorityQueue}) keyed by max-potential.</li>
 *   <li>At each iteration the node with the <em>highest</em> max-potential is
 *       popped.  Because the heap is a global frontier, the algorithm
 *       naturally finds high-EU candidates very early, which raises the
 *       Top-K threshold quickly and enables aggressive future pruning.</li>
 *   <li>If the popped node's max-potential is below the current Top-K
 *       threshold, the search terminates: since the heap is ordered by
 *       max-potential, no remaining node can ever beat the threshold.</li>
 *   <li>Otherwise the node is joined with each of its sibling extensions
 *       (items with higher index in the ordered list), and each valid
 *       joined list is pushed back into the priority queue.</li>
 * </ol>
 *
 * <h2>Probabilistic Adaptation</h2>
 * All utility quantities — EU, ΣR, EP — are derived from the uncertain
 * database model where each item in each transaction carries an existence
 * probability.  The max-potential therefore reflects expected (probability-
 * weighted) utility, not deterministic utility.
 *
 * <h2>Complexity Note</h2>
 * The heap can grow large when the search space is wide, so this strategy
 * is memory-intensive compared to DFS.  For very large databases consider
 * bounding heap size or switching to a hybrid (BeFS for top levels, DFS
 * for leaves).
 *
 * @author Meg
 * @version 5.0
 * 
 * @see ISearchStrategy
 * @see DepthFirstSearch
 * @see BreadthFirstSearch
 */
public class BestFirstSearch implements ISearchStrategy {

    // =========================================================================
    // Inner Node: wraps a joined PULList together with its extension siblings
    // =========================================================================

    /**
     * A node in the Best-First Search frontier.
     *
     * <p>Holds:
     * <ul>
     *   <li>{@code pulList}   — the utility list of the current itemset X</li>
     *   <li>{@code siblings}  — the ordered list of extensions that can be
     *       appended to X to form X∪{i} (items with higher rank than all
     *       items currently in X)</li>
     *   <li>{@code maxPotential} — cached EU(X)+ΣR(X), used as heap key</li>
     * </ul>
     */
    private static final class Node {

        final PULList pulList;
        final List<PULList> siblings;
        final double maxPotential;

        Node(PULList pulList, List<PULList> siblings) {
            this.pulList      = pulList;
            this.siblings     = siblings;
            // Probabilistic max-potential: EU(X) + ΣR(X)
            // This is an upper bound on EU of any extension of X.
            this.maxPotential = pulList.getMaxPotential();
        }
    }

    /**
     * Comparator: highest max-potential first (max-heap via reversed natural
     * ordering, since {@link PriorityQueue} is a min-heap by default).
     */
    private static final Comparator<Node> MAX_POTENTIAL_DESC =
            Comparator.comparingDouble((Node n) -> n.maxPotential).reversed();

    // =========================================================================
    // Fields
    // =========================================================================

    /** Join strategy — swappable at construction time. */
    private final IJoinStrategy joinStrategy;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Constructs a Best-First Search strategy with the given join variant.
     *
     * @param joinStrategy join strategy to use (e.g., {@code PULListJoin})
     */
    public BestFirstSearch(IJoinStrategy joinStrategy) {
        this.joinStrategy = joinStrategy;
    }

    // =========================================================================
    // ISearchStrategy
    // =========================================================================

    /**
     * Explores all extensions of {@code prefix} using a global max-potential
     * priority queue, updating the Top-K manager along the way.
     *
     * <p>Algorithm outline:
     * <pre>
     *   frontier ← max-heap()
     *   for each ext in extensions:
     *       joined ← join(prefix, ext)
     *       if valid: push Node(joined, extensions[i+1..]) into frontier
     *
     *   while frontier not empty:
     *       node ← frontier.poll()                            // highest potential
     *       if node.maxPotential < threshold: break           // global pruning
     *       tryAdd(node.pulList)                              // register result
     *       for each sib in node.siblings:
     *           joined ← join(node.pulList, sib)
     *           if valid and passes pruning: push into frontier
     * </pre>
     *
     * @param prefix     the root prefix utility list (typically an empty prefix
     *                   whose extensions are 1-itemsets)
     * @param extensions ordered candidate extension lists for {@code prefix}
     * @param topK       Top-K manager — threshold rises as good itemsets are found
     * @param pruning    pruning oracle
     * @param stats      statistics collector
     * @param profiler   performance profiler
     */
    @Override
    public void search(PULList prefix,
                       List<PULList> extensions,
                       ITopKManager topK,
                       IPruningStrategy pruning,
                       StatisticsCollector stats,
                       PerformanceProfiler profiler) {

        if (extensions == null || extensions.isEmpty()) return;

        // ---------------------------------------------------------------------
        // Phase 1: Seed the frontier with all valid 1-item extensions of prefix
        // ---------------------------------------------------------------------
        PriorityQueue<Node> frontier = new PriorityQueue<>(
                Math.max(extensions.size(), 16), MAX_POTENTIAL_DESC);

        for (int i = 0; i < extensions.size(); i++) {
            PULList ext = extensions.get(i);
            double threshold = topK.getThreshold();

            // PTWU pruning before even attempting join
            if (pruning.shouldPruneByPTWU(ext.getPtwu(), threshold)) {
                stats.incrementPtwuPruned();
                stats.incrementCandidatesPruned();
                continue;
            }

            // Join prefix ∪ {ext}
            profiler.stop(PerformanceProfiler.Component.SEARCH);
            profiler.start(PerformanceProfiler.Component.JOIN);
            PULList joined = joinStrategy.join(prefix, ext, threshold);
            profiler.stop(PerformanceProfiler.Component.JOIN);
            profiler.start(PerformanceProfiler.Component.SEARCH);

            if (joined == null || joined.isEmpty()) continue;

            stats.incrementCandidatesGenerated();
            stats.incrementUtilityListsCreated();

            // EP pruning: if EP(X) < minProbability, this subtree is dead
            profiler.stop(PerformanceProfiler.Component.SEARCH);
            profiler.start(PerformanceProfiler.Component.PRUNING);
            boolean epPruned = pruning.shouldPruneByEP(joined.getExistentialProbability());
            profiler.stop(PerformanceProfiler.Component.PRUNING);
            profiler.start(PerformanceProfiler.Component.SEARCH);

            if (epPruned) {
                stats.incrementEpPruned();
                stats.incrementCandidatesPruned();
                continue;
            }

            // EU+R pruning: EU(X)+ΣR(X) < threshold means even the best
            // extension of X cannot beat the current threshold
            profiler.stop(PerformanceProfiler.Component.SEARCH);
            profiler.start(PerformanceProfiler.Component.PRUNING);
            boolean euPruned = pruning.shouldPruneByEU(
                    joined.getSumEU(), joined.getSumRemaining(), threshold);
            profiler.stop(PerformanceProfiler.Component.PRUNING);
            profiler.start(PerformanceProfiler.Component.SEARCH);

            if (euPruned) {
                stats.incrementEuPruned();
                stats.incrementCandidatesPruned();
                continue;
            }

            // Build the sibling list: extensions that can still extend `joined`
            // (items with higher rank, i.e., index > i)
            List<PULList> siblings = buildSiblings(extensions, i + 1,
                    topK.getThreshold(), pruning, stats);

            frontier.add(new Node(joined, siblings));
        }

        // ------------------------------------------------------------------
        // Phase 2: Expand the most-promising node repeatedly
        // ------------------------------------------------------------------
        while (!frontier.isEmpty()) {

            Node node = frontier.poll();
            double threshold = topK.getThreshold();

            // ---------------------------------------------------------------
            // Global threshold pruning: the heap is ordered by maxPotential.
            // If the best remaining node cannot beat the threshold, no node
            // ever will → terminate search.
            // ---------------------------------------------------------------
            if (node.maxPotential < threshold - AlgorithmConfig.EPSILON) {
                stats.incrementBulkBranchPruned();
                break; // all remaining nodes are dominated
            }

            // ---------------------------------------------------------------
            // Try to register this node as a Top-K result
            // ---------------------------------------------------------------
            if (pruning.qualifiesForTopK(node.pulList, threshold)) {
                profiler.stop(PerformanceProfiler.Component.SEARCH);
                profiler.start(PerformanceProfiler.Component.TOPK);
                topK.tryAdd(node.pulList.getItemset(),
                            node.pulList.getSumEU(),
                            node.pulList.getExistentialProbability());
                profiler.stop(PerformanceProfiler.Component.TOPK);
                profiler.start(PerformanceProfiler.Component.SEARCH);
            }

            // ---------------------------------------------------------------
            // Generate children: join node.pulList with each sibling
            // ---------------------------------------------------------------
            List<PULList> siblings = node.siblings;
            for (int i = 0; i < siblings.size(); i++) {
                PULList sib = siblings.get(i);
                threshold = topK.getThreshold(); // refresh after every tryAdd

                // Fast PTWU gate
                if (pruning.shouldPruneByPTWU(sib.getPtwu(), threshold)) {
                    stats.incrementPtwuPruned();
                    stats.incrementCandidatesPruned();
                    continue;
                }

                // Join
                profiler.stop(PerformanceProfiler.Component.SEARCH);
                profiler.start(PerformanceProfiler.Component.JOIN);
                PULList joined = joinStrategy.join(node.pulList, sib, threshold);
                profiler.stop(PerformanceProfiler.Component.JOIN);
                profiler.start(PerformanceProfiler.Component.SEARCH);

                if (joined == null || joined.isEmpty()) continue;

                stats.incrementCandidatesGenerated();
                stats.incrementUtilityListsCreated();

                threshold = topK.getThreshold();

                // EP pruning
                profiler.stop(PerformanceProfiler.Component.SEARCH);
                profiler.start(PerformanceProfiler.Component.PRUNING);
                boolean epPruned = pruning.shouldPruneByEP(
                        joined.getExistentialProbability());
                profiler.stop(PerformanceProfiler.Component.PRUNING);
                profiler.start(PerformanceProfiler.Component.SEARCH);

                if (epPruned) {
                    stats.incrementEpPruned();
                    stats.incrementCandidatesPruned();
                    continue;
                }

                // EU+R pruning
                profiler.stop(PerformanceProfiler.Component.SEARCH);
                profiler.start(PerformanceProfiler.Component.PRUNING);
                boolean euPruned = pruning.shouldPruneByEU(
                        joined.getSumEU(), joined.getSumRemaining(), threshold);
                profiler.stop(PerformanceProfiler.Component.PRUNING);
                profiler.start(PerformanceProfiler.Component.SEARCH);

                if (euPruned) {
                    stats.incrementEuPruned();
                    stats.incrementCandidatesPruned();
                    continue;
                }

                // The child is promising — build its own sibling list and
                // push it onto the global frontier
                List<PULList> childSiblings = buildSiblings(siblings, i + 1,
                        topK.getThreshold(), pruning, stats);

                frontier.add(new Node(joined, childSiblings));
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds the sibling list for the next node, filtering out items whose
     * PTWU already falls below the current threshold (cheap pre-filter to
     * avoid pushing useless entries into the heap).
     *
     * @param source     the full ordered extension/sibling list
     * @param startIndex first index to include (items 0..startIndex-1 are already used)
     * @param threshold  current Top-K threshold
     * @param pruning    pruning strategy
     * @param stats      statistics collector
     * @return filtered sibling list (may be empty)
     */
    private List<PULList> buildSiblings(List<PULList> source,
                                        int startIndex,
                                        double threshold,
                                        IPruningStrategy pruning,
                                        StatisticsCollector stats) {
        List<PULList> result = new ArrayList<>(source.size() - startIndex);
        for (int j = startIndex; j < source.size(); j++) {
            PULList s = source.get(j);
            if (s.getPtwu() >= threshold - AlgorithmConfig.EPSILON) {
                result.add(s);
            } else {
                stats.incrementPtwuPruned();
            }
        }
        return result;
    }
}