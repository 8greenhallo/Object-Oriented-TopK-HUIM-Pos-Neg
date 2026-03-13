package ootkhuimunpnu.vntm.search;

import ootkhuimunpnu.vntm.monitoring.PerformanceProfiler;
import ootkhuimunpnu.vntm.monitoring.StatisticsCollector;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.join.IJoinStrategy;
import ootkhuimunpnu.vntm.pruning.IPruningStrategy;
import ootkhuimunpnu.vntm.topk.ITopKManager;
import ootkhuimunpnu.vntm.utility.PULList;

import java.util.*;
import java.util.logging.Logger;

/**
 * Breadth-First Search (BFS) implementation of the itemset search strategy.
 *
 * <h2>Core Idea</h2>
 * Where Depth-First Search commits to a single branch before exploring
 * siblings, BFS processes the search space <em>level by level</em>:
 * all 1-itemsets first, then all 2-itemsets, and so on.  This gives an
 * accurate picture of the utility distribution at each size before
 * diving deeper, making early Top-K registration more uniform across
 * branches.
 *
 * <h2>Search Process</h2>
 * <pre>
 *   currentLevel  ← seed(prefix, extensions)   // all valid 1-item extensions
 *
 *   while currentLevel is not empty:
 *       nextLevel ← []
 *       for each node in currentLevel:
 *           tryAdd(node)                        // register as Top-K candidate
 *           for each sibling of node:
 *               joined ← join(node, sibling)
 *               if passes all pruning checks:
 *                   nextLevel.add(joined)
 *       currentLevel ← nextLevel               // descend one level
 * </pre>
 *
 * <h2>Probabilistic Adaptation</h2>
 * Every validity check uses the uncertain-database quantities stored in
 * {@link PULList}: {@code EU(X) + ΣR(X)} as the upper-bound potential,
 * {@code EP(X)} as the existential-probability gate, and {@code PTWU(X)}
 * as the cheap pre-filter.  All three must pass before a joined list
 * enters the next level.
 *
 * <h2>Memory Considerations</h2>
 * BFS stores an <em>entire level</em> of PULList objects in memory
 * simultaneously.  For wide, shallow databases this can be substantial.
 * The implementation includes:
 * <ul>
 *   <li>A configurable {@code MAX_LEVEL_SIZE} guard that drops further
 *       expansion when the next-level list would exceed a safe bound
 *       (see {@link #MAX_LEVEL_SIZE}).  The constant can be lowered via
 *       the constructor overload that accepts an explicit limit.</li>
 *   <li>Aggressive PTWU pre-filtering in {@link #buildSiblings} to
 *       avoid materialising joins that will immediately be pruned.</li>
 *   <li>Explicit {@code null} assignment of the spent current-level list
 *       at the top of each iteration so the GC can reclaim it while the
 *       next level is still being built.</li>
 * </ul>
 *
 * @author Meg
 * @version 5.0
 * 
 * @see ISearchStrategy
 * @see DepthFirstSearch
 * @see BestFirstSearch
 */
public class BreadthFirstSearch implements ISearchStrategy {

    private static final Logger LOG =
            Logger.getLogger(BreadthFirstSearch.class.getName());

    // =========================================================================
    // Memory guard
    // =========================================================================

    /**
     * Default maximum number of PULList nodes allowed in the next-level
     * buffer at any one time.
     *
     * <p>When this limit is reached, BFS stops adding new nodes for that
     * level and logs a warning.  The already-collected nodes are still
     * processed normally, so the search remains correct — it simply may
     * miss some deep extensions beyond the cut.  Adjust downward on
     * memory-constrained deployments or upward for large, sparse datasets.
     *
     * <p>Rule of thumb: a PULList with N supporting transactions uses
     * roughly {@code 40·N + 200} bytes.  With a median transaction count
     * of 1 000 that is ~40 KB per node, so 10 000 nodes ≈ 400 MB.
     */
    public static final int DEFAULT_MAX_LEVEL_SIZE = 10_000;

    private final int maxLevelSize;

    // =========================================================================
    // Injected strategy
    // =========================================================================

    /** Join strategy — swappable at construction time. */
    private final IJoinStrategy joinStrategy;

    // =========================================================================
    // Inner Node: pairs a PULList with its ordered sibling list
    // =========================================================================

    /**
     * A node in the BFS level buffer.
     *
     * <p>Holds:
     * <ul>
     *   <li>{@code pulList}  — the utility list of the current itemset X</li>
     *   <li>{@code siblings} — ordered list of extension PULLists that can be
     *       appended to X (items with strictly higher rank than all items in X)</li>
     * </ul>
     * Keeping siblings co-located with the node avoids re-scanning the
     * full extension list at every level.
     */
    private static final class Node {
        final PULList       pulList;
        final List<PULList> siblings;

        Node(PULList pulList, List<PULList> siblings) {
            this.pulList  = pulList;
            this.siblings = siblings;
        }
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Constructs a BFS strategy with the given join variant and the
     * {@link #DEFAULT_MAX_LEVEL_SIZE default} memory guard.
     *
     * @param joinStrategy join strategy to use (e.g., {@code PULListJoin})
     */
    public BreadthFirstSearch(IJoinStrategy joinStrategy) {
        this(joinStrategy, DEFAULT_MAX_LEVEL_SIZE);
    }

    /**
     * Constructs a BFS strategy with an explicit memory guard.
     *
     * @param joinStrategy join strategy to use
     * @param maxLevelSize maximum number of nodes buffered per BFS level;
     *                     must be positive
     */
    public BreadthFirstSearch(IJoinStrategy joinStrategy, int maxLevelSize) {
        if (maxLevelSize <= 0) throw new IllegalArgumentException(
                "maxLevelSize must be positive, got: " + maxLevelSize);
        this.joinStrategy = joinStrategy;
        this.maxLevelSize = maxLevelSize;
    }

    // =========================================================================
    // ISearchStrategy
    // =========================================================================

    /**
     * Explores all extensions of {@code prefix} level by level, updating
     * the Top-K manager at each valid node encountered.
     *
     * <h3>Level 0 (seeding)</h3>
     * Each element of {@code extensions} is joined with {@code prefix} to
     * produce a 1-item extension (relative to the prefix).  Valid joined
     * lists become the initial {@code currentLevel}.
     *
     * <h3>Subsequent levels</h3>
     * For every node in {@code currentLevel}, its {@code siblings} are
     * joined with it one by one.  Valid children accumulate in
     * {@code nextLevel}.  After all nodes in the current level are
     * processed, {@code currentLevel} is replaced by {@code nextLevel}
     * and the loop repeats.
     *
     * <h3>Termination</h3>
     * The loop ends when {@code nextLevel} is empty (no more valid
     * extensions exist) or when every remaining candidate is pruned by
     * the three guards (PTWU → EP → EU+R).
     *
     * @param prefix     current prefix utility list (often the virtual root)
     * @param extensions ordered candidate extension lists for {@code prefix}
     * @param topK       Top-K manager; threshold rises as good itemsets are found
     * @param pruning    pruning oracle (PTWU, EP, EU+R)
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

        // ------------------------------------------------------------------
        // Phase 1 — Seeding: build the initial (level-1) node list by
        // joining prefix with each of its extensions.
        // ------------------------------------------------------------------
        List<Node> currentLevel = new ArrayList<>(extensions.size());

        for (int i = 0; i < extensions.size(); i++) {
            PULList ext       = extensions.get(i);
            double  threshold = topK.getThreshold();

            // Gate 1 — cheap PTWU pre-filter (no join needed)
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
            
            // Refresh threshold after every join, since it may have changed 
            // if another thread found a new Top-K result in the meantime.
            threshold = topK.getThreshold();

            // Gate 2 — EP pruning
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

            // Gate 3 — EU + ΣR pruning:
            // EU(X) + ΣR(X) is the upper bound on EU of any extension of X.
            // If this is below threshold no child can ever qualify.
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

            // Register as a Top-K candidate at this level
            tryRegister(joined, topK, pruning, profiler);

            // Build the ordered sibling list: extensions[i+1..] filtered by PTWU
            List<PULList> siblings = buildSiblings(
                    extensions, i + 1, topK.getThreshold(), pruning, stats);

            currentLevel.add(new Node(joined, siblings));
        }

        // ------------------------------------------------------------------
        // Phase 2 — Level-by-level BFS expansion
        // ------------------------------------------------------------------
        int level = 2; // we have already processed level 1 above

        while (!currentLevel.isEmpty()) {

            // ----------------------------------------------------------------
            // MEMORY GUARD: if the current level is already huge, warn and
            // stop further expansion.  The results collected so far remain
            // valid — we simply do not go deeper.
            // ----------------------------------------------------------------
            if (currentLevel.size() > maxLevelSize) {
                LOG.warning(String.format(
                        "[BreadthFirstSearch] Level %d has %d nodes — exceeds " +
                        "maxLevelSize=%d. Truncating to prevent OOM. " +
                        "Reduce k, raise minProb, or lower maxLevelSize.",
                        level, currentLevel.size(), maxLevelSize));
                currentLevel = currentLevel.subList(0, maxLevelSize);
            }

            List<Node> nextLevel = new ArrayList<>();
            boolean    levelFull = false;

            for (Node node : currentLevel) {
                List<PULList> siblings  = node.siblings;
                double        threshold = topK.getThreshold();

                // ------------------------------------------------------------
                // Bulk branch pruning:
                // EU(node) + ΣR(node) is an upper bound on any child's EU.
                // If it is below the current threshold this entire subtree is dead.
                // ------------------------------------------------------------
                double nodePotential = node.pulList.getSumEU()
                                     + node.pulList.getSumRemaining();
                if (nodePotential < threshold - AlgorithmConfig.EPSILON) {
                    stats.incrementBulkBranchPruned();
                    continue; // skip all children of this node
                }

                for (int i = 0; i < siblings.size(); i++) {
                    PULList sib = siblings.get(i);
                    threshold   = topK.getThreshold(); // refresh after every tryAdd

                    // Gate 1 — PTWU
                    if (pruning.shouldPruneByPTWU(sib.getPtwu(), threshold)) {
                        stats.incrementPtwuPruned();
                        stats.incrementCandidatesPruned();
                        continue;
                    }

                    // Join node.pulList ∪ {sib}
                    profiler.stop(PerformanceProfiler.Component.SEARCH);
                    profiler.start(PerformanceProfiler.Component.JOIN);
                    PULList joined = joinStrategy.join(node.pulList, sib, threshold);
                    profiler.stop(PerformanceProfiler.Component.JOIN);
                    profiler.start(PerformanceProfiler.Component.SEARCH);

                    if (joined == null || joined.isEmpty()) continue;

                    stats.incrementCandidatesGenerated();
                    stats.incrementUtilityListsCreated();

                    threshold = topK.getThreshold();

                    // Gate 2 — EP
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

                    // Gate 3 — EU + ΣR
                    // Probabilistic adaptation: EU(X) + ΣR(X) ≥ threshold is
                    // the necessary condition for any extension of X to qualify.
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

                    // Register this node as a Top-K candidate (level n result)
                    tryRegister(joined, topK, pruning, profiler);

                    // Build children's sibling list for the next BFS level
                    List<PULList> childSiblings = buildSiblings(
                            siblings, i + 1, topK.getThreshold(), pruning, stats);

                    // --------------------------------------------------------
                    // MEMORY GUARD: stop filling nextLevel once it is full.
                    // Nodes already added will still be processed next iteration.
                    // --------------------------------------------------------
                    if (!levelFull) {
                        nextLevel.add(new Node(joined, childSiblings));
                        if (nextLevel.size() >= maxLevelSize) {
                            LOG.warning(String.format(
                                    "[BreadthFirstSearch] nextLevel buffer full at " +
                                    "level %d (%d nodes). Halting further expansion " +
                                    "at this level to prevent OOM.",
                                    level + 1, maxLevelSize));
                            levelFull = true;
                            // Do NOT break — we still want to tryRegister the
                            // remaining siblings, we just stop buffering them.
                        }
                    }
                    // Even when levelFull we continue the inner loop so that
                    // tryRegister() is still called for remaining siblings.
                }
            }

            // Discard the current level — allow GC to reclaim memory
            // before the next level starts growing.
            currentLevel = null; // NOSONAR (intentional reassignment for GC)
            currentLevel = nextLevel;
            level++;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Registers a joined utility list with the Top-K manager if it
     * qualifies (EU ≥ threshold and EP ≥ minProbability).
     *
     * <p>Called at every BFS level — we may find a Top-K result at depth 1,
     * depth 2, or any deeper level, so we must check at each level.
     *
     * @param joined   the newly constructed utility list for itemset X
     * @param topK     Top-K manager
     * @param pruning  pruning oracle ({@code qualifiesForTopK} check)
     * @param profiler performance profiler
     */
    private void tryRegister(PULList joined,
                              ITopKManager topK,
                              IPruningStrategy pruning,
                              PerformanceProfiler profiler) {
        double threshold = topK.getThreshold();
        if (pruning.qualifiesForTopK(joined, threshold)) {
            profiler.stop(PerformanceProfiler.Component.SEARCH);
            profiler.start(PerformanceProfiler.Component.TOPK);
            topK.tryAdd(joined.getItemset(),
                        joined.getSumEU(),
                        joined.getExistentialProbability());
            profiler.stop(PerformanceProfiler.Component.TOPK);
            profiler.start(PerformanceProfiler.Component.SEARCH);
        }
    }

    /**
     * Builds the sibling list for a child node: items from {@code source}
     * starting at {@code startIndex}, pre-filtered by PTWU.
     *
     * <p>This acts as a cheap pre-pruning step before any joins are
     * attempted, keeping both the sibling list and future join counts small.
     *
     * @param source     full ordered extension / sibling list
     * @param startIndex first index to include
     * @param threshold  current Top-K threshold
     * @param pruning    pruning oracle
     * @param stats      statistics collector
     * @return filtered sibling list (may be empty)
     */
    private List<PULList> buildSiblings(List<PULList> source,
                                         int startIndex,
                                         double threshold,
                                         IPruningStrategy pruning,
                                         StatisticsCollector stats) {
        List<PULList> result = new ArrayList<>(
                Math.max(0, source.size() - startIndex));
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