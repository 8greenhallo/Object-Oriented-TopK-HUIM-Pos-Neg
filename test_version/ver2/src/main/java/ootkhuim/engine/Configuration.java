package engine;

import java.util.*;

/**
 * Configuration class for the mining algorithm
 */
public class Configuration {
    // Algorithm parameters
    private final Map<Integer, Double> itemProfits;
    private final int k;
    private final double minProbability;
    
    // Numerical stability constants
    private final double epsilon;
    private final double logEpsilon;

    // Default values
    private static final double EPSILON = 1e-10;
    private static final double LOG_EPSILON = -700; // exp(-700) ≈ 0
    
    // Progress reporting interval in mining process
    public static final int PROGRESS_INTERVAL = 10000; // Check every i transactions
    public static final int DEFAULT_MEMORY_CHECK_INTERVAL = 10;  // Check every 10 items
    
    /**
     * Private constructor used by Builder pattern.
     */
    private Configuration(Builder builder) {
        this.k = builder.k;
        this.minProbability = builder.minProbability;
        this.itemProfits = Collections.unmodifiableMap(new HashMap<>(builder.itemProfits));
        this.epsilon = builder.epsilon;
        this.logEpsilon = builder.logEpsilon;
        
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
    }

    // Getters for algorithm parameters
    public Map<Integer, Double> getItemProfits() { return itemProfits; }
    public int getK() { return k; }
    public double getMinProbability() { return minProbability; }

    // Getters for numerical constants
    public double getEpsilon() { return epsilon; }
    public double getLogEpsilon() { return logEpsilon; }

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

    /**
     * Builder pattern for creating Configuration instances.
     */
    public static class Builder {
        private int k = 10;
        private double minProbability = 0.1;
        private Map<Integer, Double> itemProfits = new HashMap<>();
        private double epsilon = EPSILON;
        private double logEpsilon = LOG_EPSILON;
        //private boolean enableDetailedLogging = false; // No need
        //private boolean enableMemoryMonitoring = true;
        //private int memoryCheckInterval = DEFAULT_MEMORY_CHECK_INTERVAL;  // same as DEFAULT_MEMORY_CHECK_INTERVAL
        //private long maxMemoryUsage = Runtime.getRuntime().maxMemory();
        //private boolean enableEarlyTermination = true;
        //private boolean enableAdvancedPruning = true;
        //private int utilityListCacheSize = DEFAULT_UTILITY_LIST_CACHE_SIZE;
        //private boolean sortResultsByUtility = true;
        //private boolean includeDetailedStatistics = true;
        
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
            return this;
        }
        
        public Builder setLogEpsilon(double logEpsilon) {
            this.logEpsilon = logEpsilon;
            return this;
        }
        
        /*public Builder enableDetailedLogging(boolean enable) {
            this.enableDetailedLogging = enable;
            return this;
        }
        
        public Builder enableMemoryMonitoring(boolean enable) {
            this.enableMemoryMonitoring = enable;
            return this;
        }
        
        public Builder setMemoryCheckInterval(int interval) {
            this.memoryCheckInterval = interval;
            return this;
        }
        
        public Builder setMaxMemoryUsage(long maxMemoryUsage) {
            this.maxMemoryUsage = maxMemoryUsage;
            return this;
        }
        
        public Builder enableEarlyTermination(boolean enable) {
            this.enableEarlyTermination = enable;
            return this;
        }
        
        public Builder enableAdvancedPruning(boolean enable) {
            this.enableAdvancedPruning = enable;
            return this;
        }
        
        public Builder setUtilityListCacheSize(int size) {
            this.utilityListCacheSize = size;
            return this;
        }
        
        public Builder sortResultsByUtility(boolean enable) {
            this.sortResultsByUtility = enable;
            return this;
        }
        
        public Builder includeDetailedStatistics(boolean enable) {
            this.includeDetailedStatistics = enable;
            return this;
        }
        */
        
        public Configuration build() {
            return new Configuration(this);
        }
    }

    @Override
    public String toString() {
        return String.format("Configuration{k=%d, minProb=%.4f, items=%d}", 
                           k, minProbability, itemProfits.size());
    }
}