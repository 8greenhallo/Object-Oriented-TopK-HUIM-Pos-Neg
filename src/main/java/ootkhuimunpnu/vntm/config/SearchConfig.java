package ootkhuimunpnu.vntm.config;

/**
 * Configuration for the search strategy used during mining.
 *
 * <p>Controls <em>how</em> the itemset search space is traversed
 * (depth-first, breadth-first, best-first) and sets any limits on
 * the traversal depth.
 *
 * <p>The strategy type is resolved by {@link mining.MiningEngine} at
 * initialisation time to select the correct {@link search.ISearchStrategy}
 * implementation.
 * 
 * <p>Change when Build SearchConfig in {@link App} to switch strategies.
 *
 * @author Meg
 * @version 5.0
 */
public class SearchConfig {

    /**
     * Enumeration of supported search strategies.
     * New strategies can be added here without changing existing code.
     */
    public enum StrategyType {
        /** Classic depth-first search — default, proven to work well with pruning. */
        DFS,
        /** Breadth-first search — explores level-by-level; higher memory cost. */
        BFS,
        /** Best-first search — heuristic ordering; intended for future implementation. */
        BEST_FIRST
    }

    /** Selected search strategy. */
    private final StrategyType strategyType;

    /**
     * Maximum recursion depth (number of items in a candidate itemset).
     * 0 or negative means unlimited.
     */
    private final int maxDepth;

    /**
     * Minimum number of extensions required before switching to parallel processing.
     * When below this threshold the engine uses sequential execution even if
     * a parallel engine is available.
     */
    private final int parallelThreshold;

    /**
     * Minimum number of extensions per ForkJoin task before splitting further.
     * Controls the task granularity for work-stealing schedulers.
     */
    private final int taskGranularity;

    /**
     * Whether to enable dynamic threshold updating during the search.
     * When {@code true}, the mining threshold is refreshed as better itemsets
     * are found, enabling more aggressive pruning.
     */
    private final boolean dynamicThreshold;

    /**
     * Constructs a SearchConfig from the given builder.
     *
     * @param builder populated builder
     */
    private SearchConfig(Builder builder) {
        this.strategyType    = builder.strategyType;
        this.maxDepth        = builder.maxDepth;
        this.parallelThreshold = builder.parallelThreshold;
        this.taskGranularity = builder.taskGranularity;
        this.dynamicThreshold = builder.dynamicThreshold;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return search strategy type */
    public StrategyType getStrategyType() { return strategyType; }

    /** @return maximum search depth (0 = unlimited) */
    public int getMaxDepth() { return maxDepth; }

    /** @return minimum extension count for parallel processing */
    public int getParallelThreshold() { return parallelThreshold; }

    /** @return ForkJoin task granularity */
    public int getTaskGranularity() { return taskGranularity; }

    /** @return whether dynamic threshold updating is enabled */
    public boolean isDynamicThreshold() { return dynamicThreshold; }

    @Override
    public String toString() {
        return String.format("SearchConfig{strategy=%s, maxDepth=%d, dynamicThreshold=%b}",
                strategyType, maxDepth, dynamicThreshold);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Fluent builder for {@link SearchConfig}.
     */
    public static class Builder {

        private StrategyType strategyType = StrategyType.DFS;
        private int maxDepth = 0; // unlimited
        private int parallelThreshold = 30;
        private int taskGranularity = 7;
        private boolean dynamicThreshold = true;

        /** @param type strategy type to use
         *  @return this */
        public Builder setStrategyType(StrategyType type) { this.strategyType = type; return this; }

        /** @param depth max depth (0 = unlimited)
         *  @return this */
        public Builder setMaxDepth(int depth) { this.maxDepth = depth; return this; }

        /** @param threshold extension count to trigger parallelism
         *  @return this */
        public Builder setParallelThreshold(int threshold) {
            this.parallelThreshold = threshold; return this;
        }

        /** @param granularity ForkJoin granularity
         *  @return this */
        public Builder setTaskGranularity(int granularity) {
            this.taskGranularity = granularity; return this;
        }

        /** @param dynamic enable dynamic threshold updates
         *  @return this */
        public Builder setDynamicThreshold(boolean dynamic) {
            this.dynamicThreshold = dynamic; return this;
        }

        /** @return built {@link SearchConfig} */
        public SearchConfig build() {
            return new SearchConfig(this);
        }
    }
}