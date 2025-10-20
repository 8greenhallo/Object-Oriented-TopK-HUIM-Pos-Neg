package engine;

import java.util.*;
import java.io.*;

/**
 * Configuration class for OOTK-HUIM-UN-PNU algorithm parameters and settings.
 * Encapsulates all configurable aspects of the mining process with
 * validation and default value management.
 * 
 * @author Meg
 * @version 1.0
 */
public class Configuration {
    
    // Core algorithm parameters
    private int k;
    private double minProbability;
    private Map<Integer, Double> itemProfits;
    
    // Memory management
    private long maxMemoryUsage;
    private boolean enableGarbageCollection;
    private int memoryMonitoringInterval;
    
    // Numerical stability
    private double epsilon;
    private double logEpsilon;
    
    // Output and debugging
    private boolean enableDetailedLogging;
    private boolean enablePerformanceMonitoring;
    private boolean exportIntermediateResults;
    private String outputDirectory;
    
    // Algorithm-specific settings
    private boolean enableRTWUPruning;
    private boolean enableExistentialProbabilityPruning;
    private boolean enableUtilityUpperBoundPruning;
    private boolean enableBulkPruning;
    
    // Default values
    private static final int DEFAULT_K = 10;
    private static final double DEFAULT_MIN_PROBABILITY = 0.1;
    private static final double DEFAULT_EPSILON = 1e-10;
    private static final double DEFAULT_LOG_EPSILON = -700;
    private static final long DEFAULT_MAX_MEMORY = Runtime.getRuntime().maxMemory();
    
    /**
     * Creates a configuration with default values.
     */
    public Configuration() {
        setDefaults();
    }
    
    /**
     * Creates a configuration with specified K and minimum probability.
     * 
     * @param k Number of top itemsets to find
     * @param minProbability Minimum existential probability threshold
     * @param itemProfits Map of item IDs to profit values
     */
    public Configuration(int k, double minProbability, Map<Integer, Double> itemProfits) {
        setDefaults();
        setK(k);
        setMinProbability(minProbability);
        setItemProfits(itemProfits);
    }
    
    /**
     * Sets all parameters to default values.
     */
    private void setDefaults() {
        this.k = DEFAULT_K;
        this.minProbability = DEFAULT_MIN_PROBABILITY;
        this.itemProfits = new HashMap<>();
        
        this.maxMemoryUsage = DEFAULT_MAX_MEMORY;
        this.enableGarbageCollection = true;
        this.memoryMonitoringInterval = 1000; // milliseconds
        
        this.epsilon = DEFAULT_EPSILON;
        this.logEpsilon = DEFAULT_LOG_EPSILON;
        
        this.enableDetailedLogging = false;
        this.enablePerformanceMonitoring = true;
        this.exportIntermediateResults = false;
        this.outputDirectory = System.getProperty("java.io.tmpdir");
        
        this.enableRTWUPruning = true;
        this.enableExistentialProbabilityPruning = true;
        this.enableUtilityUpperBoundPruning = true;
        this.enableBulkPruning = true;
    }
    
    // ==================== CORE PARAMETERS ====================
    
    /**
     * Gets the number of top itemsets to find.
     * 
     * @return K value
     */
    public int getK() {
        return k;
    }
    
    /**
     * Sets the number of top itemsets to find.
     * 
     * @param k K value (must be positive)
     * @throws IllegalArgumentException if k <= 0
     */
    public void setK(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("K must be positive, got: " + k);
        }
        this.k = k;
    }
    
    /**
     * Gets the minimum existential probability threshold.
     * 
     * @return Minimum probability
     */
    public double getMinProbability() {
        return minProbability;
    }
    
    /**
     * Sets the minimum existential probability threshold.
     * 
     * @param minProbability Minimum probability (must be between 0 and 1)
     * @throws IllegalArgumentException if minProbability is not in [0, 1]
     */
    public void setMinProbability(double minProbability) {
        if (minProbability < 0.0 || minProbability > 1.0) {
            throw new IllegalArgumentException(
                "Minimum probability must be between 0 and 1, got: " + minProbability);
        }
        this.minProbability = minProbability;
    }
    
    /**
     * Gets the item profit mapping.
     * 
     * @return Map of item IDs to profit values
     */
    public Map<Integer, Double> getItemProfits() {
        return Collections.unmodifiableMap(itemProfits);
    }
    
    /**
     * Sets the item profit mapping.
     * 
     * @param itemProfits Map of item IDs to profit values
     * @throws IllegalArgumentException if itemProfits is null or empty
     */
    public void setItemProfits(Map<Integer, Double> itemProfits) {
        if (itemProfits == null || itemProfits.isEmpty()) {
            throw new IllegalArgumentException("Item profits cannot be null or empty");
        }
        this.itemProfits = new HashMap<>(itemProfits);
    }
    
    // ==================== PERFORMANCE PARAMETERS ====================
    
    // ==================== MEMORY MANAGEMENT ====================
    
    /**
     * Gets the maximum memory usage limit.
     * 
     * @return Maximum memory in bytes
     */
    public long getMaxMemoryUsage() {
        return maxMemoryUsage;
    }
    
    /**
     * Sets the maximum memory usage limit.
     * 
     * @param maxMemoryUsage Maximum memory in bytes
     * @throws IllegalArgumentException if memory limit is negative
     */
    public void setMaxMemoryUsage(long maxMemoryUsage) {
        if (maxMemoryUsage < 0) {
            throw new IllegalArgumentException("Max memory usage cannot be negative");
        }
        this.maxMemoryUsage = maxMemoryUsage;
    }
    
    /**
     * Checks if garbage collection is enabled.
     * 
     * @return True if garbage collection is enabled
     */
    public boolean isGarbageCollectionEnabled() {
        return enableGarbageCollection;
    }
    
    /**
     * Enables or disables automatic garbage collection.
     * 
     * @param enableGarbageCollection True to enable garbage collection
     */
    public void setGarbageCollectionEnabled(boolean enableGarbageCollection) {
        this.enableGarbageCollection = enableGarbageCollection;
    }
    
    /**
     * Gets the memory monitoring interval.
     * 
     * @return Monitoring interval in milliseconds
     */
    public int getMemoryMonitoringInterval() {
        return memoryMonitoringInterval;
    }
    
    /**
     * Sets the memory monitoring interval.
     * 
     * @param memoryMonitoringInterval Monitoring interval in milliseconds
     * @throws IllegalArgumentException if interval is negative
     */
    public void setMemoryMonitoringInterval(int memoryMonitoringInterval) {
        if (memoryMonitoringInterval < 0) {
            throw new IllegalArgumentException("Memory monitoring interval cannot be negative");
        }
        this.memoryMonitoringInterval = memoryMonitoringInterval;
    }
    
    // ==================== NUMERICAL STABILITY ====================
    
    /**
     * Gets the epsilon value for numerical comparisons.
     * 
     * @return Epsilon value
     */
    public double getEpsilon() {
        return epsilon;
    }
    
    /**
     * Sets the epsilon value for numerical comparisons.
     * 
     * @param epsilon Epsilon value (must be positive)
     * @throws IllegalArgumentException if epsilon is not positive
     */
    public void setEpsilon(double epsilon) {
        if (epsilon <= 0) {
            throw new IllegalArgumentException("Epsilon must be positive");
        }
        this.epsilon = epsilon;
    }
    
    /**
     * Gets the log epsilon value for logarithmic calculations.
     * 
     * @return Log epsilon value
     */
    public double getLogEpsilon() {
        return logEpsilon;
    }
    
    /**
     * Sets the log epsilon value for logarithmic calculations.
     * 
     * @param logEpsilon Log epsilon value (should be negative)
     * @throws IllegalArgumentException if logEpsilon is positive
     */
    public void setLogEpsilon(double logEpsilon) {
        if (logEpsilon > 0) {
            throw new IllegalArgumentException("Log epsilon should be negative");
        }
        this.logEpsilon = logEpsilon;
    }
    
    // ==================== OUTPUT AND DEBUGGING ====================
    
    /**
     * Checks if detailed logging is enabled.
     * 
     * @return True if detailed logging is enabled
     */
    public boolean isDetailedLoggingEnabled() {
        return enableDetailedLogging;
    }
    
    /**
     * Enables or disables detailed logging.
     * 
     * @param enableDetailedLogging True to enable detailed logging
     */
    public void setDetailedLoggingEnabled(boolean enableDetailedLogging) {
        this.enableDetailedLogging = enableDetailedLogging;
    }
    
    /**
     * Checks if performance monitoring is enabled.
     * 
     * @return True if performance monitoring is enabled
     */
    public boolean isPerformanceMonitoringEnabled() {
        return enablePerformanceMonitoring;
    }
    
    /**
     * Enables or disables performance monitoring.
     * 
     * @param enablePerformanceMonitoring True to enable performance monitoring
     */
    public void setPerformanceMonitoringEnabled(boolean enablePerformanceMonitoring) {
        this.enablePerformanceMonitoring = enablePerformanceMonitoring;
    }
    
    /**
     * Checks if intermediate results export is enabled.
     * 
     * @return True if intermediate results export is enabled
     */
    public boolean isIntermediateResultsExportEnabled() {
        return exportIntermediateResults;
    }
    
    /**
     * Enables or disables intermediate results export.
     * 
     * @param exportIntermediateResults True to enable intermediate results export
     */
    public void setIntermediateResultsExportEnabled(boolean exportIntermediateResults) {
        this.exportIntermediateResults = exportIntermediateResults;
    }
    
    /**
     * Gets the output directory.
     * 
     * @return Output directory path
     */
    public String getOutputDirectory() {
        return outputDirectory;
    }
    
    /**
     * Sets the output directory.
     * 
     * @param outputDirectory Output directory path
     * @throws IllegalArgumentException if directory path is null or empty
     */
    public void setOutputDirectory(String outputDirectory) {
        if (outputDirectory == null || outputDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Output directory cannot be null or empty");
        }
        this.outputDirectory = outputDirectory;
    }
    
    // ==================== PRUNING STRATEGIES ====================
    
    /**
     * Checks if RTWU pruning is enabled.
     * 
     * @return True if RTWU pruning is enabled
     */
    public boolean isRTWUPruningEnabled() {
        return enableRTWUPruning;
    }
    
    /**
     * Enables or disables RTWU pruning.
     * 
     * @param enableRTWUPruning True to enable RTWU pruning
     */
    public void setRTWUPruningEnabled(boolean enableRTWUPruning) {
        this.enableRTWUPruning = enableRTWUPruning;
    }
    
    /**
     * Checks if existential probability pruning is enabled.
     * 
     * @return True if existential probability pruning is enabled
     */
    public boolean isExistentialProbabilityPruningEnabled() {
        return enableExistentialProbabilityPruning;
    }
    
    /**
     * Enables or disables existential probability pruning.
     * 
     * @param enableExistentialProbabilityPruning True to enable existential probability pruning
     */
    public void setExistentialProbabilityPruningEnabled(boolean enableExistentialProbabilityPruning) {
        this.enableExistentialProbabilityPruning = enableExistentialProbabilityPruning;
    }
    
    /**
     * Checks if utility upper bound pruning is enabled.
     * 
     * @return True if utility upper bound pruning is enabled
     */
    public boolean isUtilityUpperBoundPruningEnabled() {
        return enableUtilityUpperBoundPruning;
    }
    
    /**
     * Enables or disables utility upper bound pruning.
     * 
     * @param enableUtilityUpperBoundPruning True to enable utility upper bound pruning
     */
    public void setUtilityUpperBoundPruningEnabled(boolean enableUtilityUpperBoundPruning) {
        this.enableUtilityUpperBoundPruning = enableUtilityUpperBoundPruning;
    }
    
    /**
     * Checks if bulk pruning is enabled.
     * 
     * @return True if bulk pruning is enabled
     */
    public boolean isBulkPruningEnabled() {
        return enableBulkPruning;
    }
    
    /**
     * Enables or disables bulk pruning.
     * 
     * @param enableBulkPruning True to enable bulk pruning
     */
    public void setBulkPruningEnabled(boolean enableBulkPruning) {
        this.enableBulkPruning = enableBulkPruning;
    }
    
    // ==================== VALIDATION AND UTILITIES ====================
    
    /**
     * Validates the current configuration settings.
     * 
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        List<String> errors = new ArrayList<>();
        
        if (k <= 0) {
            errors.add("K must be positive");
        }
        
        if (minProbability < 0.0 || minProbability > 1.0) {
            errors.add("Minimum probability must be between 0 and 1");
        }
        
        if (itemProfits.isEmpty()) {
            errors.add("Item profits cannot be empty");
        }
        
        if (epsilon <= 0) {
            errors.add("Epsilon must be positive");
        }
        
        if (logEpsilon > 0) {
            errors.add("Log epsilon should be negative");
        }
        
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            errors.add("Cannot create output directory: " + outputDirectory);
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Configuration validation failed: " + 
                                          String.join(", ", errors));
        }
    }
    
    /**
     * Creates a copy of this configuration.
     * 
     * @return Deep copy of this configuration
     */
    public Configuration copy() {
        Configuration copy = new Configuration();
        
        copy.k = this.k;
        copy.minProbability = this.minProbability;
        copy.itemProfits = new HashMap<>(this.itemProfits);
        
        copy.maxMemoryUsage = this.maxMemoryUsage;
        copy.enableGarbageCollection = this.enableGarbageCollection;
        copy.memoryMonitoringInterval = this.memoryMonitoringInterval;
        
        copy.epsilon = this.epsilon;
        copy.logEpsilon = this.logEpsilon;
        
        copy.enableDetailedLogging = this.enableDetailedLogging;
        copy.enablePerformanceMonitoring = this.enablePerformanceMonitoring;
        copy.exportIntermediateResults = this.exportIntermediateResults;
        copy.outputDirectory = this.outputDirectory;
        
        copy.enableRTWUPruning = this.enableRTWUPruning;
        copy.enableExistentialProbabilityPruning = this.enableExistentialProbabilityPruning;
        copy.enableUtilityUpperBoundPruning = this.enableUtilityUpperBoundPruning;
        copy.enableBulkPruning = this.enableBulkPruning;
        
        return copy;
    }
    
    /**
     * Loads configuration from a properties file.
     * 
     * @param filename Properties file path
     * @return Configuration loaded from file
     * @throws IOException if file cannot be read
     */
    public static Configuration loadFromFile(String filename) throws IOException {
        Configuration config = new Configuration();
        Properties props = new Properties();
        
        try (FileInputStream fis = new FileInputStream(filename)) {
            props.load(fis);
        }
        
        // Load basic parameters
        config.setK(Integer.parseInt(props.getProperty("k", String.valueOf(DEFAULT_K))));
        config.setMinProbability(Double.parseDouble(props.getProperty("minProbability", 
                                                                      String.valueOf(DEFAULT_MIN_PROBABILITY))));
        
        // Load boolean flags
        config.setDetailedLoggingEnabled(Boolean.parseBoolean(props.getProperty("enableDetailedLogging", "false")));
        config.setPerformanceMonitoringEnabled(Boolean.parseBoolean(props.getProperty("enablePerformanceMonitoring", "true")));
        
        // Load pruning settings
        config.setRTWUPruningEnabled(Boolean.parseBoolean(props.getProperty("enableRTWUPruning", "true")));
        config.setExistentialProbabilityPruningEnabled(Boolean.parseBoolean(props.getProperty("enableExistentialProbabilityPruning", "true")));
        config.setUtilityUpperBoundPruningEnabled(Boolean.parseBoolean(props.getProperty("enableUtilityUpperBoundPruning", "true")));
        config.setBulkPruningEnabled(Boolean.parseBoolean(props.getProperty("enableBulkPruning", "true")));
        
        return config;
    }
    
    /**
     * Saves configuration to a properties file.
     * 
     * @param filename Properties file path
     * @throws IOException if file cannot be written
     */
    public void saveToFile(String filename) throws IOException {
        Properties props = new Properties();
        
        // Basic parameters
        props.setProperty("k", String.valueOf(k));
        props.setProperty("minProbability", String.valueOf(minProbability));
        
        // Memory parameters
        props.setProperty("maxMemoryUsage", String.valueOf(maxMemoryUsage));
        props.setProperty("enableGarbageCollection", String.valueOf(enableGarbageCollection));
        props.setProperty("memoryMonitoringInterval", String.valueOf(memoryMonitoringInterval));
        
        // Numerical parameters
        props.setProperty("epsilon", String.valueOf(epsilon));
        props.setProperty("logEpsilon", String.valueOf(logEpsilon));
        
        // Output parameters
        props.setProperty("enableDetailedLogging", String.valueOf(enableDetailedLogging));
        props.setProperty("enablePerformanceMonitoring", String.valueOf(enablePerformanceMonitoring));
        props.setProperty("exportIntermediateResults", String.valueOf(exportIntermediateResults));
        props.setProperty("outputDirectory", outputDirectory);
        
        // Pruning parameters
        props.setProperty("enableRTWUPruning", String.valueOf(enableRTWUPruning));
        props.setProperty("enableExistentialProbabilityPruning", String.valueOf(enableExistentialProbabilityPruning));
        props.setProperty("enableUtilityUpperBoundPruning", String.valueOf(enableUtilityUpperBoundPruning));
        props.setProperty("enableBulkPruning", String.valueOf(enableBulkPruning));
        
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            props.store(fos, "OOTK-HUIM Configuration");
        }
    }
    
    @Override
    public String toString() {
        return String.format("Configuration{k=%d, minProb=%.3f}", 
                           k, minProbability);
    }
    
    /**
     * Returns detailed string representation for debugging.
     * 
     * @return Detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration{\n");
        sb.append("  Core: k=").append(k).append(", minProb=").append(minProbability).append("\n");
        sb.append("  Items: ").append(itemProfits.size()).append(" items\n");
        sb.append("  Memory: max=").append(maxMemoryUsage / 1024 / 1024).append("MB")
          .append(", gc=").append(enableGarbageCollection).append("\n");
        sb.append("  Numerical: eps=").append(epsilon).append(", logEps=").append(logEpsilon).append("\n");
        sb.append("  Output: logging=").append(enableDetailedLogging)
          .append(", monitoring=").append(enablePerformanceMonitoring).append("\n");
        sb.append("  Pruning: RTWU=").append(enableRTWUPruning)
          .append(", EP=").append(enableExistentialProbabilityPruning)
          .append(", UB=").append(enableUtilityUpperBoundPruning)
          .append(", bulk=").append(enableBulkPruning).append("\n");
        sb.append("}");
        return sb.toString();
    }
}