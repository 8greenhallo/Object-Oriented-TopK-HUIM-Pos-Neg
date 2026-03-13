package ootkhuimunpnu.vntm.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable configuration for the HUIM algorithm.
 *
 * <p>Encapsulates all parameters needed to run a single experiment:
 * <ul>
 *   <li><b>K</b> — number of top itemsets to discover</li>
 *   <li><b>minProbability</b> — minimum existential probability EP(X) ≥ minProbability</li>
 *   <li><b>profitTable</b> — item → profit mapping (may contain negative values)</li>
 * </ul>
 *
 * <p>Numerical constants:
 * <pre>
 *   EPSILON     = 1e-10  (floating-point equality tolerance)
 *   LOG_EPSILON = -700   (≈ log of the smallest representable double ≈ 5×10⁻³²⁴)
 * </pre>
 *
 * <p>Use the nested {@link Builder} to construct instances:
 * <pre>
 *   AlgorithmConfig cfg = new AlgorithmConfig.Builder()
 *       .setK(100)
 *       .setMinProbability(0.1)
 *       .setProfitTable(profits)
 *       .build();
 * </pre>
 *
 * @author Meg
 * @version 5.0
 */
public class AlgorithmConfig {

    // =========================================================================
    // Global numerical stability constants
    // =========================================================================

    /**
     * Floating-point equality tolerance.
     * Two double values a, b are treated as equal when |a - b| &lt; EPSILON.
     */
    public static final double EPSILON = 1e-10;

    /**
     * Log-space sentinel for negligibly small probabilities.
     * exp(-700) ≈ 9.86 × 10⁻³⁰⁵ — effectively zero in IEEE 754 double precision.
     * Any computed log-probability below this value is clamped to LOG_EPSILON.
     */
    public static final double LOG_EPSILON = -700.0;

    /**
     * Default JVM memory limit used as an upper bound for memory monitoring.
     * Mining stops early if usage exceeds this.
     * 
     * Not OS memory.
     */
    public static final long MAX_MEMORY_BYTES = Runtime.getRuntime().maxMemory();

    // =========================================================================
    // Instance fields (immutable after construction)
    // =========================================================================

    /** Number of top itemsets to discover. */
    private final int k;

    /**
     * Minimum existential probability threshold.
     * Only itemsets with EP(X) ≥ minProbability are returned.
     */
    private final double minProbability;

    /** Global item → profit table. Profits may be negative. */
    private final Map<Integer, Double> profitTable;

    /** Enable verbose progress logging. */
    private final boolean verbose;

    /** 
     * Flag to enable or disable memory usage tracking during mining.
     */
    private final boolean memoryMonitoringEnabled;

    /**
     * How often (every N processed items) to check memory usage.
     * Lower = more accurate peak tracking; higher = less overhead.
     */
    private final int memoryCheckInterval;

    /**
     * How often (every N items) to print progress during the mining phase.
     */
    private final int progressReportInterval;

    // =========================================================================
    // Constructor (private — use Builder)
    // =========================================================================

    private AlgorithmConfig(Builder b) {
        this.k = b.k;
        this.minProbability = b.minProbability;
        this.profitTable = Collections.unmodifiableMap(new HashMap<>(b.profitTable));
        this.verbose = b.verbose;
        this.memoryMonitoringEnabled = b.memoryMonitoringEnabled;
        this.memoryCheckInterval = b.memoryCheckInterval;
        this.progressReportInterval = b.progressReportInterval;
    }

    // =========================================================================
    // Getters
    // =========================================================================

    /** @return K (number of top itemsets) */
    public int getK() { return k; }

    /** @return minimum existential probability threshold */
    public double getMinProbability() { return minProbability; }

    /** @return unmodifiable profit table (item ID → profit) */
    public Map<Integer, Double> getProfitTable() { return profitTable; }

    /** @return whether verbose logging is enabled */
    public boolean isVerbose() { return verbose; }

    /** @return whether memory monitoring is active */
    public boolean isMemoryMonitoringEnabled() { return memoryMonitoringEnabled; }

    /** @return memory check interval (items between checks) */
    public int getMemoryCheckInterval() { return memoryCheckInterval; }

    /** @return progress report interval (items between log lines) */
    public int getProgressReportInterval() { return progressReportInterval; }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    /**
     * Returns the profit for item {@code id}, or 0.0 if not found.
     *
     * @param itemId item identifier
     * @return profit value
     */
    public double getProfit(int itemId) {
        return profitTable.getOrDefault(itemId, 0.0);
    }

    /**
     * Checks whether item {@code id} has strictly positive profit.
     *
     * @param itemId item identifier
     * @return {@code true} if profit &gt; EPSILON
     */
    public boolean hasPositiveProfit(int itemId) {
        Double p = profitTable.get(itemId);
        return p != null && p > EPSILON;
    }

    /**
     * Validates this configuration, throwing {@link IllegalArgumentException}
     * if any parameter is out of range.
     *
     * @throws IllegalArgumentException on invalid configuration
     */
    public void validate() {
        if (k <= 0) throw new IllegalArgumentException("K must be > 0, got: " + k);
        if (minProbability < 0.0 || minProbability > 1.0)
            throw new IllegalArgumentException("minProbability must be in [0,1], got: " + minProbability);
        if (profitTable == null || profitTable.isEmpty())
            throw new IllegalArgumentException("profitTable must not be empty");
        if (memoryCheckInterval <= 0)
            throw new IllegalArgumentException("memoryCheckInterval must be > 0");
        if (progressReportInterval <= 0)
            throw new IllegalArgumentException("progressReportInterval must be > 0");
    }

    @Override
    public String toString() {
        return String.format("AlgorithmConfig{k=%d, minProb=%.4f, items=%d, verbose=%b}",
                k, minProbability, profitTable.size(), verbose);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Fluent builder for {@link AlgorithmConfig}.
     */
    public static class Builder {

        private int k = 10;
        private double minProbability = 0.1;
        private Map<Integer, Double> profitTable = new HashMap<>();
        private boolean verbose = true;
        private boolean memoryMonitoringEnabled = true; // Default to enabled
        private int memoryCheckInterval = 10;
        private int progressReportInterval = 10;

        /** @param k number of top itemsets
         *  @return this builder */
        public Builder setK(int k) { this.k = k; return this; }

        /** @param minProbability minimum EP threshold
         *  @return this builder */
        public Builder setMinProbability(double minProbability) {
            this.minProbability = minProbability; return this;
        }

        /** @param profitTable item → profit map
         *  @return this builder */
        public Builder setProfitTable(Map<Integer, Double> profitTable) {
            this.profitTable = new HashMap<>(profitTable); return this;
        }

        /** @param verbose enable verbose logging
         *  @return this builder */
        public Builder setVerbose(boolean verbose) { this.verbose = verbose; return this; }

        /** @param enabled set to true to monitor heap usage */
        public Builder setMemoryMonitoringEnabled(boolean enabled) {
            this.memoryMonitoringEnabled = enabled;
            return this;
        }

        /** @param interval items between memory checks
         *  @return this builder */
        public Builder setMemoryCheckInterval(int interval) {
            this.memoryCheckInterval = interval; return this;
        }

        /** @param interval items between progress log lines
         *  @return this builder */
        public Builder setProgressReportInterval(int interval) {
            this.progressReportInterval = interval; return this;
        }

        /**
         * Builds and validates the configuration.
         *
         * @return a validated {@link AlgorithmConfig}
         * @throws IllegalArgumentException if parameters are invalid
         */
        public AlgorithmConfig build() {
            AlgorithmConfig cfg = new AlgorithmConfig(this);
            cfg.validate();
            return cfg;
        }
    }
}