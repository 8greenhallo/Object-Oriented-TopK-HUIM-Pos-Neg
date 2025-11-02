package ootkhuimunpnu.ver03.engine;

import java.util.*;

/**
 * Configuration class for the mining algorithm using hybrid approach for both 
 * default static constant(global usage) and override via builder (flexibility).
 * 
 * Design Principles:
 * - Builder Pattern: Fluent interface for configuration
 * - Validation: Parameters validated at build time
 * - Immutability: Configuration cannot be changed after creation
 * - Default Values: Sensible defaults for optional parameters
 * 
 * MODIFICATION:
 * - Add fields and methods to support log-space probability calculations
 * 
 * @author Meg
 * @version 3.0
 */
public class Configuration {
    // Algorithm parameters
    private final Map<Integer, Double> itemProfits;
    private final int k;
    private final double minProbability;
    
    // Numerical stability constants
    private final double epsilon;
    private final double logEpsilon;

    // Performance settings
    private final boolean enableMemoryMonitoring;
    private final int memoryCheckInterval;
    private final int progressInterval;

    // Output settings
    private final boolean includeDetailedStatistics;

    // Default values, set as public static finals for global access, specially in statistic class
    public static final double EPSILON = 1e-10;
    public static final double LOG_EPSILON = -700; // exp(-700) ≈ 0

    public static final long MAX_MEMORY_USAGE = Runtime.getRuntime().maxMemory(); // Use JVM max memory as default
    
    public static final int MEMORY_CHECK_INTERVAL = 10;  // Check every 10 items (mining process)
    public static final int PROGRESS_INTERVAL = 10000;   // Report every i transactions (mining process)
    
    /**
     * Private constructor used by instance field via Builder
     */
    private Configuration(Builder builder) {
        this.k = builder.k;
        this.minProbability = builder.minProbability;
        this.itemProfits = Collections.unmodifiableMap(new HashMap<>(builder.itemProfits));
        this.epsilon = builder.epsilon;
        this.logEpsilon = builder.logEpsilon;
        this.enableMemoryMonitoring = builder.enableMemoryMonitoring;
        this.memoryCheckInterval = builder.memoryCheckInterval;
        this.progressInterval = builder.progressInterval;
        this.includeDetailedStatistics = builder.includeDetailedStatistics;
        
        // Validation
        validate();
    }

    /**
     * Validates the configuration parameters.
     */
    private void validate() {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        
        if (minProbability < 0.0 || minProbability > 1.0) {
            throw new IllegalArgumentException("minProbability must be between 0.0 and 1.0");
        }
        
        if (itemProfits.isEmpty()) {
            throw new IllegalArgumentException("itemProfits cannot be empty");
        }

        if (epsilon <= 0) {
            throw new IllegalArgumentException("epsilon must be positive");
        }
        
        if (logEpsilon >= 0) {
            throw new IllegalArgumentException("logEpsilon must be negative");
        }
        
        if (memoryCheckInterval <= 0) {
            throw new IllegalArgumentException("memoryCheckInterval must be positive");
        }

        if (progressInterval <= 0) {
            throw new IllegalArgumentException("progressInterval must be positive");
        }
    }

    // Getters for algorithm parameters
    public Map<Integer, Double> getItemProfits() { return itemProfits; }
    public int getK() { return k; }
    public double getMinProbability() { return minProbability; }

    // Getters for numerical constants
    public double getEpsilon() { return epsilon; }
    public double getLogEpsilon() { return logEpsilon; }

    // Getters for performance settings
    public boolean isMemoryMonitoringEnabled() { return enableMemoryMonitoring; }
    public int getMemoryCheckInterval() { return memoryCheckInterval; }
    public int getProgressInterval() { return progressInterval; }
    
    // Getters for output settings
    public boolean isIncludeDetailedStatisticsEnabled() { return includeDetailedStatistics; }

    public int getItemCount() {
        return itemProfits.size();
    }

    public boolean hasItem(int item) {
        return itemProfits.containsKey(item);
    }

    public Double getItemProfit(int item) {
        return itemProfits.get(item);
    }

    public double getItemProfitOrDefault(int item, double defaultValue) {
        return itemProfits.getOrDefault(item, defaultValue);
    }

    // ==================== Builder Pattern ====================

    /**
     * Builder pattern for creating Configuration instances.
     */
    public static class Builder {
        private int k = 10;
        private double minProbability = 0.1;
        private Map<Integer, Double> itemProfits = new HashMap<>();
        private double epsilon = EPSILON;
        private double logEpsilon = LOG_EPSILON;
        private boolean enableMemoryMonitoring = true;
        private int memoryCheckInterval = MEMORY_CHECK_INTERVAL;
        private int progressInterval = PROGRESS_INTERVAL;
        private boolean includeDetailedStatistics = true;
        
        public Builder setK(int k) {
            this.k = k;
            return this;
        }
        
        public Builder setMinProbability(double minProbability) {
            this.minProbability = minProbability;
            return this;
        }
        
        public Builder setItemProfits(Map<Integer, Double> itemProfits) {
            this.itemProfits = new HashMap<>(itemProfits);
            return this;
        }
        
        public Builder addItemProfit(int item, double profit) {
            this.itemProfits.put(item, profit);
            return this;
        }
        
        public Builder setEpsilon(double epsilon) {
            this.epsilon = epsilon;
            System.out.println("Epsilon privately set to: " + epsilon);
            return this;
        }
        
        public Builder setLogEpsilon(double logEpsilon) {
            this.logEpsilon = logEpsilon;
            System.out.println("Log Epsilon privately set to: " + logEpsilon);
            return this;
        }
        
        public Builder enableMemoryMonitoring(boolean enable) {
            this.enableMemoryMonitoring = enable;
            return this;
        }
        
        public Builder setMemoryCheckInterval(int memoryCheckInterval) {
            this.memoryCheckInterval = memoryCheckInterval;
            return this;
        }

        public Builder setProgressInterval(int progressInterval) {
            this.progressInterval = progressInterval;
            return this;
        }
        
        public Builder includeDetailedStatistics(boolean enable) {
            this.includeDetailedStatistics = enable;
            return this;
        }
        
        public Configuration build() {
            return new Configuration(this);
        }
    }
    /**
     * Gets a summary of the configuration.
     */
    public String configurationStatistic() {
        return String.format(" - K : %d\n"+
                             " - MinProb : %.4f\n"+
                             " - Items : %d\n"+
                             " - Epsilon : %.1e\n"+
                             " - Log Epsilon : %.1e\n"+
                             " - Enable Memory Monitory? : %b\n"+
                             " - Max Memory : %dMB\n"+
                             " - Memory Check Interval : %d\n"+
                             " - Progress Interval : %d\n"+
                             " - Detailed Statistic? : %b",
                             k, minProbability, itemProfits.size(), epsilon, logEpsilon,
                             enableMemoryMonitoring, MAX_MEMORY_USAGE / (1024 * 1024),
                             memoryCheckInterval, progressInterval, includeDetailedStatistics);
    }

    @Override
    public String toString() {
        return String.format("Configuration{k=%d, minProb=%.4f, items=%d}", 
                           k, minProbability, itemProfits.size());
    }
}