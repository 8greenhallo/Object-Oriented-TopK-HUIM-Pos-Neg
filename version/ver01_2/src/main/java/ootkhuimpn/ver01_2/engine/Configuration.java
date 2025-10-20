package engine;

import java.util.*;

/**
 * Configuration class for OOTK-HUIM-U± algorithm parameters and settings.
 * This class encapsulates all configurable aspects of the mining process
 * and provides validation for parameter values.
 * 
 * @author Meg
 * @version 1.2
 */
public class Configuration {
    // Algorithm parameters
    private final int k;
    private final double minProbability;
    private final Map<Integer, Double> itemProfits;
    
    // Numerical stability constants
    private final double epsilon;
    private final double logEpsilon;
    
    // Performance settings
    private final boolean enableDetailedLogging;
    private final boolean enableMemoryMonitoring;
    private final int memoryCheckInterval;
    private final long maxMemoryUsage;
    
    // Optimization settings
    private final boolean enableEarlyTermination;
    private final boolean enableAdvancedPruning;
    private final int utilityListCacheSize;
    
    // Output settings
    private final boolean sortResultsByUtility;
    private final boolean includeDetailedStatistics;
    
    // Default values
    public static final double DEFAULT_EPSILON = 1e-10;
    public static final double DEFAULT_LOG_EPSILON = -700.0;
    public static final int DEFAULT_MEMORY_CHECK_INTERVAL = 10;  // Check every 10 items
    public static final int DEFAULT_UTILITY_LIST_CACHE_SIZE = 10000;
    
    /**
     * Private constructor used by Builder pattern.
     */
    private Configuration(Builder builder) {
        this.k = builder.k;
        this.minProbability = builder.minProbability;
        this.itemProfits = Collections.unmodifiableMap(new HashMap<>(builder.itemProfits));
        this.epsilon = builder.epsilon;
        this.logEpsilon = builder.logEpsilon;
        this.enableDetailedLogging = builder.enableDetailedLogging;
        this.enableMemoryMonitoring = builder.enableMemoryMonitoring;
        this.memoryCheckInterval = builder.memoryCheckInterval;
        this.maxMemoryUsage = builder.maxMemoryUsage;
        this.enableEarlyTermination = builder.enableEarlyTermination;
        this.enableAdvancedPruning = builder.enableAdvancedPruning;
        this.utilityListCacheSize = builder.utilityListCacheSize;
        this.sortResultsByUtility = builder.sortResultsByUtility;
        this.includeDetailedStatistics = builder.includeDetailedStatistics;
        
        validate();
    }
    
    /**
     * Validates the configuration parameters.
     * 
     * @throws IllegalArgumentException if any parameter is invalid
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
        
        if (maxMemoryUsage <= 0) {
            throw new IllegalArgumentException("maxMemoryUsage must be positive");
        }
        
        if (utilityListCacheSize <= 0) {
            throw new IllegalArgumentException("utilityListCacheSize must be positive");
        }
    }
    
    // Getters for algorithm parameters
    public int getK() { return k; }
    public double getMinProbability() { return minProbability; }
    public Map<Integer, Double> getItemProfits() { return itemProfits; }
    
    // Getters for numerical constants
    public double getEpsilon() { return epsilon; }
    public double getLogEpsilon() { return logEpsilon; }
    
    // Getters for performance settings
    public boolean isDetailedLoggingEnabled() { return enableDetailedLogging; }
    public boolean isMemoryMonitoringEnabled() { return enableMemoryMonitoring; }
    public int getMemoryCheckInterval() { return memoryCheckInterval; }
    public long getMaxMemoryUsage() { return maxMemoryUsage; }
    
    // Getters for optimization settings
    public boolean isEarlyTerminationEnabled() { return enableEarlyTermination; }
    public boolean isAdvancedPruningEnabled() { return enableAdvancedPruning; }
    public int getUtilityListCacheSize() { return utilityListCacheSize; }
    
    // Getters for output settings
    public boolean shouldSortResultsByUtility() { return sortResultsByUtility; }
    public boolean shouldIncludeDetailedStatistics() { return includeDetailedStatistics; }
    
    /**
     * Gets the profit for a specific item.
     * 
     * @param item The item ID
     * @return The profit value, or null if item not found
     */
    public Double getItemProfit(int item) {
        return itemProfits.get(item);
    }
    
    /**
     * Gets the number of items with profit information.
     * 
     * @return Number of items
     */
    public int getItemCount() {
        return itemProfits.size();
    }
    
    /**
     * Gets all item IDs with profit information.
     * 
     * @return Set of item IDs
     */
    public Set<Integer> getItems() {
        return itemProfits.keySet();
    }
    
    /**
     * Checks if an item has positive profit.
     * 
     * @param item The item ID
     * @return true if profit > 0, false otherwise
     */
    public boolean hasPositiveProfit(int item) {
        Double profit = itemProfits.get(item);
        return profit != null && profit > 0;
    }
    
    /**
     * Checks if an item has negative profit.
     * 
     * @param item The item ID
     * @return true if profit < 0, false otherwise
     */
    public boolean hasNegativeProfit(int item) {
        Double profit = itemProfits.get(item);
        return profit != null && profit < 0;
    }
    
    /**
     * Gets statistics about item profits.
     * 
     * @return Profit statistics
     */
    public ProfitStatistics getProfitStatistics() {
        if (itemProfits.isEmpty()) {
            return new ProfitStatistics(0, 0, 0, 0.0, 0.0, 0.0);
        }
        
        int positiveCount = 0, negativeCount = 0;
        double sum = 0.0, min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        
        for (double profit : itemProfits.values()) {
            if (profit > 0) positiveCount++;
            else if (profit < 0) negativeCount++;
            
            sum += profit;
            min = Math.min(min, profit);
            max = Math.max(max, profit);
        }
        
        double average = sum / itemProfits.size();
        
        return new ProfitStatistics(itemProfits.size(), positiveCount, negativeCount, average, min, max);
    }
    
    /**
     * Creates a copy of this configuration with modified parameters.
     * 
     * @return Builder initialized with current configuration
     */
    public Builder toBuilder() {
        return new Builder()
            .setK(k)
            .setMinProbability(minProbability)
            .setItemProfits(itemProfits)
            .setEpsilon(epsilon)
            .setLogEpsilon(logEpsilon)
            .enableDetailedLogging(enableDetailedLogging)
            .enableMemoryMonitoring(enableMemoryMonitoring)
            .setMemoryCheckInterval(memoryCheckInterval)
            .setMaxMemoryUsage(maxMemoryUsage)
            .enableEarlyTermination(enableEarlyTermination)
            .enableAdvancedPruning(enableAdvancedPruning)
            .setUtilityListCacheSize(utilityListCacheSize)
            .sortResultsByUtility(sortResultsByUtility)
            .includeDetailedStatistics(includeDetailedStatistics);
    }
    
    /**
     * Statistics about item profits.
     */
    public static class ProfitStatistics {
        private final int totalItems;
        private final int positiveItems;
        private final int negativeItems;
        private final double averageProfit;
        private final double minProfit;
        private final double maxProfit;
        
        public ProfitStatistics(int totalItems, int positiveItems, int negativeItems,
                               double averageProfit, double minProfit, double maxProfit) {
            this.totalItems = totalItems;
            this.positiveItems = positiveItems;
            this.negativeItems = negativeItems;
            this.averageProfit = averageProfit;
            this.minProfit = minProfit;
            this.maxProfit = maxProfit;
        }
        
        public int getTotalItems() { return totalItems; }
        public int getPositiveItems() { return positiveItems; }
        public int getNegativeItems() { return negativeItems; }
        public int getZeroItems() { return totalItems - positiveItems - negativeItems; }
        public double getAverageProfit() { return averageProfit; }
        public double getMinProfit() { return minProfit; }
        public double getMaxProfit() { return maxProfit; }
        
        @Override
        public String toString() {
            return String.format("ProfitStats[total=%d, pos=%d, neg=%d, avg=%.4f, range=[%.4f, %.4f]]",
                               totalItems, positiveItems, negativeItems, averageProfit, minProfit, maxProfit);
        }
    }
    
    /**
     * Builder pattern for creating Configuration instances.
     */
    public static class Builder {
        private int k = 10;
        private double minProbability = 0.1;
        private Map<Integer, Double> itemProfits = new HashMap<>();
        private double epsilon = DEFAULT_EPSILON;
        private double logEpsilon = DEFAULT_LOG_EPSILON;
        private boolean enableDetailedLogging = false;
        private boolean enableMemoryMonitoring = true;
        private int memoryCheckInterval = DEFAULT_MEMORY_CHECK_INTERVAL;
        private long maxMemoryUsage = Runtime.getRuntime().maxMemory();
        private boolean enableEarlyTermination = true;
        private boolean enableAdvancedPruning = true;
        private int utilityListCacheSize = DEFAULT_UTILITY_LIST_CACHE_SIZE;
        private boolean sortResultsByUtility = true;
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
            return this;
        }
        
        public Builder setLogEpsilon(double logEpsilon) {
            this.logEpsilon = logEpsilon;
            return this;
        }
        
        public Builder enableDetailedLogging(boolean enable) {
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
        
        public Configuration build() {
            return new Configuration(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("Configuration[k=%d, minProb=%.4f, items=%d, eps=%.2e]",
                           k, minProbability, itemProfits.size(), epsilon);
    }
}