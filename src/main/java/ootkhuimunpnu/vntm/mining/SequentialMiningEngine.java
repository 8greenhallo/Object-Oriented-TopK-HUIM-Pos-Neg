package ootkhuimunpnu.vntm.mining;

import java.util.ArrayList;
import java.util.List;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.monitoring.PerformanceProfiler;
import ootkhuimunpnu.vntm.monitoring.StatisticsCollector;
import ootkhuimunpnu.vntm.pruning.IPruningStrategy;
import ootkhuimunpnu.vntm.search.ISearchStrategy;
import ootkhuimunpnu.vntm.topk.ITopKManager;
import ootkhuimunpnu.vntm.utility.PULList;

/**
 * Sequential (single-threaded) implementation of the HUIM mining engine.
 *
 * <p>Iterates over single-item utility lists in ascending PTWU rank order,
 * pruning entire branches when the item's PTWU falls below the current
 * mining threshold, then delegates recursive search to the injected
 * {@link ISearchStrategy}.
 *
 * <h3>Design rationale</h3>
 * All execution logic is kept in this class; search traversal (DFS/BFS) and
 * pruning decisions are fully delegated to strategies.  This means the engine
 * never changes when we swap, for example, DFS for BFS.
 *
 * @author Meg
 * @version 5.0
 */
public class SequentialMiningEngine extends MiningEngine {

    /**
     * Constructs the sequential engine with all required strategies.
     *
     * @param config          algorithm configuration
     * @param topKManager     top-K manager strategy
     * @param pruningStrategy pruning decision strategy
     * @param searchStrategy  search traversal strategy
     * @param profiler        performance profiler
     * @param stats           statistics collector
     */
    public SequentialMiningEngine(AlgorithmConfig config,
                                   ITopKManager topKManager,
                                   IPruningStrategy pruningStrategy,
                                   ISearchStrategy searchStrategy,
                                   PerformanceProfiler profiler,
                                   StatisticsCollector stats) {
        super(config, topKManager, pruningStrategy, searchStrategy, profiler, stats);
    }

    /**
     * Executes sequential, single-threaded mining.
     *
     * <p>For each item i (in ascending PTWU rank order):
     * <ol>
     *   <li>Check if PTWU(i) &lt; threshold → branch prune, skip.</li>
     *   <li>Build extensions list: all items j &gt; i with PTWU(j) ≥ threshold.</li>
     *   <li>Delegate recursive search to {@link ISearchStrategy#search}.</li>
     *   <li>Periodically monitor memory usage.</li>
     * </ol>
     */
    @Override
    protected void executeMining() {
        List<Integer> sortedItems = getSortedItemsByRank();
        int total = sortedItems.size();

        for (int i = 0; i < total; i++) {
            int item       = sortedItems.get(i);
            PULList prefix = singleItemLists.get(item);
            if (prefix == null) continue;

            double threshold = topKManager.getThreshold();

            // -------------------------------------------------------------------
            // Branch pruning: if PTWU(item) < threshold, this item and ALL its
            // extensions can never reach the threshold → skip entirely
            // -------------------------------------------------------------------
            if (pruningStrategy.shouldPruneBranch(itemPTWU.get(item), threshold)) {
                stats.incrementBranchPruned();
                continue;
            }

            // -------------------------------------------------------------------
            // Build extension list: items after i with viable PTWU
            // -------------------------------------------------------------------
            List<PULList> extensions = buildExtensions(sortedItems, i);

            // -------------------------------------------------------------------
            // Recursive search (delegated to ISearchStrategy)
            // -------------------------------------------------------------------
            if (!extensions.isEmpty()) {
                profiler.start(PerformanceProfiler.Component.SEARCH);
                searchStrategy.search(prefix, extensions, topKManager, pruningStrategy,
                                      stats, profiler);
                profiler.stop(PerformanceProfiler.Component.SEARCH);
            }

            // -------------------------------------------------------------------
            // Progress and memory monitoring
            // -------------------------------------------------------------------
            if (config.isVerbose() && i % config.getProgressReportInterval() == 0) {
                System.out.printf("Progress: %d/%d items processed%n", i + 1, total);
            }

            if (config.isMemoryMonitoringEnabled() && i % config.getMemoryCheckInterval() == 0) {
                long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                stats.updatePeakMemory(used);

                if (used > AlgorithmConfig.MAX_MEMORY_BYTES * 0.95) {
                    System.err.println("[WARNING] Memory usage critical — stopping mining.");
                    break;
                }
            }
        }
    }

    /**
     * Builds the list of viable extension utility lists for the item at
     * position {@code currentIndex} in {@code sortedItems}.
     *
     * <p>An extension item j is included only if PTWU(j) ≥ current threshold.
     *
     * @param sortedItems   items sorted by ascending PTWU rank
     * @param currentIndex  current item index (extensions are items at index &gt; currentIndex)
     * @return non-empty list of extension {@link PULList}s; may be empty
     */
    private List<PULList> buildExtensions(List<Integer> sortedItems, int currentIndex) {
        List<PULList> extensions = new ArrayList<>();
        double threshold = topKManager.getThreshold();

        for (int j = currentIndex + 1; j < sortedItems.size(); j++) {
            int extItem     = sortedItems.get(j);
            PULList extList = singleItemLists.get(extItem);
            if (extList == null) continue;

            // PTWU pruning of extension candidate
            if (itemPTWU.getOrDefault(extItem, 0.0) < threshold - AlgorithmConfig.EPSILON) {
                stats.incrementPtwuPruned();
                continue;
            }
            extensions.add(extList);
        }
        return extensions;
    }
}