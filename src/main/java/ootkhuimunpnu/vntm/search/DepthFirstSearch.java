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
 * Depth-First Search (DFS) implementation of the itemset search strategy.
 *
 * <p>Recursively extends a prefix by appending one item at a time (in
 * ascending PTWU-rank order), checking pruning conditions at each step,
 * and adding qualifying itemsets to the top-K manager.
 *
 * <h3>Pruning Conditions (applied in order of cheapness)</h3>
 * <ol>
 *   <li><b>PTWU pruning</b>: extension.ptwu &lt; threshold → skip</li>
 *   <li><b>EP pruning</b>: EP(joined) &lt; minProbability → skip subtree</li>
 *   <li><b>EU+R pruning</b>: EU(joined) + ΣR(joined) &lt; threshold → skip subtree</li>
 * </ol>
 *
 * <h3>Join Strategy</h3>
 * The {@link IJoinStrategy} is injected at construction time. Swap in
 * {@link utility.StandardJoin}, {@link utility.EarlyTerminationJoin}, or
 * {@link utility.ProbabilityFilteredJoin} to benchmark different join variants
 * without changing any search or pruning logic.
 *
 * <h3>Performance Profiling</h3>
 * The profiler tracks exclusive time per component.  When this method calls
 * the join operation, it pauses the SEARCH timer and resumes the JOIN timer,
 * so the final report accurately attributes CPU time to each component.
 *
 * @author Meg
 * @version 5.0
 * 
 * @see ISearchStrategy
 * @see BestFirstSearch
 * @see BreadthFirstSearch
 */
public class DepthFirstSearch implements ISearchStrategy {

    /**
     * Join strategy — swappable at construction time.
     * Controls how two utility lists are merged into a combined list.
     */
    private final IJoinStrategy joinStrategy;

    /**
     * Constructs a DFS search strategy with the given join variant.
     *
     * @param joinStrategy join strategy to use (e.g., {@link utility.StandardJoin})
     */
    public DepthFirstSearch(IJoinStrategy joinStrategy) {
        this.joinStrategy = joinStrategy;
    }

    /**
     * Recursively searches all depth-first extensions of {@code prefix}.
     *
     * <p>Bulk branch pruning is applied first:
     * if min(prefix.ptwu, min_ext.ptwu) &lt; threshold, no extension can qualify.
     *
     * @param prefix     current prefix utility list
     * @param extensions ordered list of candidate extension lists
     * @param topK       top-K manager
     * @param pruning    pruning strategy
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
        searchRecursive(prefix, extensions, topK, pruning, stats, profiler);
    }

    /**
     * Core recursive DFS procedure.
     *
     * @param prefix     current prefix
     * @param extensions viable extensions for this level
     * @param topK       top-K manager
     * @param pruning    pruning strategy
     * @param stats      statistics
     * @param profiler   profiler
     */
    private void searchRecursive(PULList prefix,
                                  List<PULList> extensions,
                                  ITopKManager topK,
                                  IPruningStrategy pruning,
                                  StatisticsCollector stats,
                                  PerformanceProfiler profiler) {
        if (extensions == null || extensions.isEmpty()) return;

        double threshold = topK.getThreshold();

        // ------------------------------------------------------------------
        // Bulk branch pruning:
        // If min PTWU among all extensions < threshold, no extension can
        // generate an itemset above threshold → prune the entire batch.
        // ------------------------------------------------------------------
        double minExtPTWU = Double.MAX_VALUE;
        for (PULList ext : extensions) {
            if (ext.getPtwu() < minExtPTWU) minExtPTWU = ext.getPtwu();
        }
        double maxJoinedPTWU = Math.min(prefix.getPtwu(), minExtPTWU);
        if (pruning.shouldPruneBulk(maxJoinedPTWU, threshold)) {
            stats.incrementBulkBranchPruned();
            return;
        }

        // ------------------------------------------------------------------
        // Process each extension
        // ------------------------------------------------------------------
        for (int i = 0; i < extensions.size(); i++) {
            PULList extension = extensions.get(i);
            threshold = topK.getThreshold(); // refresh — may have increased

            // 1. PTWU pruning on individual extension
            if (pruning.shouldPruneByPTWU(extension.getPtwu(), threshold)) {
                stats.incrementPtwuPruned();
                stats.incrementCandidatesPruned();
                continue;
            }

            // 2. Join operation (pause SEARCH, start JOIN)
            profiler.stop(PerformanceProfiler.Component.SEARCH);
            profiler.start(PerformanceProfiler.Component.JOIN);
            PULList joined = joinStrategy.join(prefix, extension, threshold);
            profiler.stop(PerformanceProfiler.Component.JOIN);
            profiler.start(PerformanceProfiler.Component.SEARCH);

            if (joined == null || joined.isEmpty()) continue;

            stats.incrementCandidatesGenerated();
            stats.incrementUtilityListsCreated();

            threshold = topK.getThreshold();

            // 3. EP pruning: EP(X) < minProbability
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

            // 4. EU + remaining pruning: EU(X) + ΣR(X) < threshold
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

            // 5. Add to top-K if both EU and EP qualify
            if (pruning.qualifiesForTopK(joined, threshold)) {
                profiler.stop(PerformanceProfiler.Component.SEARCH);
                profiler.start(PerformanceProfiler.Component.TOPK);
                topK.tryAdd(joined.getItemset(), joined.getSumEU(),
                            joined.getExistentialProbability());
                profiler.stop(PerformanceProfiler.Component.TOPK);
                profiler.start(PerformanceProfiler.Component.SEARCH);
            }

            // 6. Recurse deeper (i+1 onward)
            if (i < extensions.size() - 1) {
                List<PULList> nextExtensions = buildNextExtensions(
                        extensions, i + 1, topK.getThreshold(), pruning, stats);

                if (!nextExtensions.isEmpty()) {
                    searchRecursive(joined, nextExtensions, topK, pruning, stats, profiler);
                }
            }
        }
    }

    /**
     * Builds the extensions list for the next recursion level.
     *
     * <p>Filters out any extension whose PTWU is below the current threshold
     * to prevent needless join work.
     *
     * @param extensions     current level extensions
     * @param startIndex     index to start from (items already processed are skipped)
     * @param threshold      current mining threshold
     * @param pruning        pruning strategy
     * @param stats          statistics collector
     * @return filtered list of viable next-level extensions
     */
    private List<PULList> buildNextExtensions(List<PULList> extensions,
                                               int startIndex,
                                               double threshold,
                                               IPruningStrategy pruning,
                                               StatisticsCollector stats) {
        List<PULList> next = new ArrayList<>();
        for (int j = startIndex; j < extensions.size(); j++) {
            PULList ext = extensions.get(j);
            if (ext.getPtwu() >= threshold - AlgorithmConfig.EPSILON) {
                next.add(ext);
            } else {
                stats.incrementPtwuPruned();
            }
        }
        return next;
    }
}