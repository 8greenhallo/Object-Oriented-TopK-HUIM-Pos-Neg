package ootkhuimunpnu.vntm.mining;

import java.util.List;
import java.util.Map;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.db.Itemset;
import ootkhuimunpnu.vntm.db.RawTransaction;
import ootkhuimunpnu.vntm.monitoring.PerformanceProfiler;
import ootkhuimunpnu.vntm.monitoring.StatisticsCollector;
import ootkhuimunpnu.vntm.preprocessing.DatabasePreprocessor;
import ootkhuimunpnu.vntm.preprocessing.ItemOrderingStrategy;
import ootkhuimunpnu.vntm.pruning.IPruningStrategy;
import ootkhuimunpnu.vntm.search.ISearchStrategy;
import ootkhuimunpnu.vntm.topk.ITopKManager;
import ootkhuimunpnu.vntm.utility.PULList;
import ootkhuimunpnu.vntm.utility.ProfitTable;

/**
 * Abstract base class providing the template method for HUIM mining.
 *
 * <p>Defines the algorithm skeleton (preprocess → single-items → mine)
 * while delegating variation points to concrete subclasses and injected
 * strategy objects.
 *
 * <h3>Strategy Composition</h3>
 * Five strategy types are injected at construction time:
 * <ol>
 *   <li>{@link ITopKManager}    — manages the top-K candidate set</li>
 *   <li>{@link IPruningStrategy} — decides when to prune</li>
 *   <li>{@link ISearchStrategy}  — controls traversal order</li>
 *   <li>{@link utility.PULListJoinOperation} — join logic (via searchStrategy)</li>
 *   <li>Execution mode — sequential or other model (selected by subclass)</li>
 * </ol>
 *
 * @author Meg
 * @version 5.0
 */
public abstract class MiningEngine implements IMiningEngine {

    // Injected strategies
    protected final AlgorithmConfig config;
    protected final ITopKManager topKManager;
    protected final IPruningStrategy pruningStrategy;
    protected final ISearchStrategy searchStrategy;

    // Preprocessing results
    protected Map<Integer, PULList> singleItemLists;
    protected Map<Integer, Double> itemPTWU;
    protected Map<Integer, Integer> itemToRank;

    // Profiling and statistics
    protected final PerformanceProfiler profiler;
    protected final StatisticsCollector stats;

    /**
     * Constructs the engine with all required strategies.
     *
     * @param config          algorithm configuration
     * @param topKManager     top-K manager strategy
     * @param pruningStrategy pruning decision strategy
     * @param searchStrategy  search traversal strategy
     * @param profiler        performance profiler
     * @param stats           statistics collector
     */
    protected MiningEngine(AlgorithmConfig config,
                            ITopKManager topKManager,
                            IPruningStrategy pruningStrategy,
                            ISearchStrategy searchStrategy,
                            PerformanceProfiler profiler,
                            StatisticsCollector stats) {
        this.config          = config;
        this.topKManager     = topKManager;
        this.pruningStrategy = pruningStrategy;
        this.searchStrategy  = searchStrategy;
        this.profiler        = profiler;
        this.stats           = stats;
    }

    /**
     * Template method: runs the full mining pipeline.
     *
     * <ol>
     *   <li>Phase 1 — Preprocessing (PTWU, ranking, utility lists)</li>
     *   <li>Phase 2 — Evaluate single-item utility lists</li>
     *   <li>Phase 3 — Recursive mining (delegated to subclass)</li>
     * </ol>
     *
     * @param rawTransactions uncertain transaction database
     * @return top-K itemsets in descending EU order
     */
    @Override
    public final List<Itemset> mine(List<RawTransaction> rawTransactions) {
        if (config.isVerbose()) printHeader(rawTransactions.size());

        // ------------------------------------------------------------------
        // Phase 1: Preprocessing
        // ------------------------------------------------------------------
        profiler.start(PerformanceProfiler.Component.PREPROCESSING);
        ProfitTable profitTable = new ProfitTable(config.getProfitTable());
        DatabasePreprocessor preprocessor = new DatabasePreprocessor(config, profitTable);
        this.singleItemLists = preprocessor.preprocess(rawTransactions);
        this.itemPTWU        = preprocessor.getItemPTWU();
        this.itemToRank      = preprocessor.getItemToRank();
        profiler.stop(PerformanceProfiler.Component.PREPROCESSING);

        if (config.isVerbose()) {
            System.out.println("Items after filtering: " + singleItemLists.size());
        }

        // ------------------------------------------------------------------
        // Phase 2: Process single-item utility lists
        // ------------------------------------------------------------------
        profiler.start(PerformanceProfiler.Component.TOPK);
        for (PULList ul : singleItemLists.values()) {
            if (pruningStrategy.qualifiesForTopK(ul, topKManager.getThreshold())) {
                topKManager.tryAdd(ul.getItemset(), ul.getSumEU(),
                                   ul.getExistentialProbability());
            }
        }
        profiler.stop(PerformanceProfiler.Component.TOPK);

        // ------------------------------------------------------------------
        // Phase 3: Recursive mining (delegated to concrete implementation)
        // ------------------------------------------------------------------
        if (config.isVerbose()) System.out.println("\n=== Phase 3: Mining ===");
        executeMining();

        List<Itemset> results = topKManager.getTopK();

        if (config.isVerbose()) printSummary(results.size());
        return results;
    }

    /**
     * Subclasses implement their specific execution strategy here
     * (sequential DFS, distributed, etc.).
     */
    protected abstract void executeMining();

    // =========================================================================
    // Shared helpers available to subclasses
    // =========================================================================

    /**
     * Returns a list of single-item keys sorted by ascending PTWU rank.
     *
     * @return sorted item list
     */
    protected List<Integer> getSortedItemsByRank() {
        return ItemOrderingStrategy.sortByRank(singleItemLists.keySet(), itemToRank);
    }

    // =========================================================================
    // Logging helpers
    // =========================================================================

    private void printHeader(int dbSize) {
        System.out.println("=== OOTK-PHUIM Mining Engine ===");
        System.out.println("Engine: " + getClass().getSimpleName());
        System.out.println("Config: " + config);
        System.out.println("Database size: " + dbSize);
        System.out.println("Available memory: " +
                (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
    }

    private void printSummary(int resultCount) {
        System.out.println("\n=== Mining Complete ===");
        System.out.println("Top-K found: " + resultCount);
        System.out.println("Final threshold: " +
                String.format("%.4f", topKManager.getThreshold()));
        stats.printReport();
        profiler.printReport();
    }
}