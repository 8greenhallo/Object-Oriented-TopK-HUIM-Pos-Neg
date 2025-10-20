package engine;

import core.*;
import java.util.*;
import java.util.stream.*;
import java.time.*;

/**
 * Core mining engine for OOTK-HUIM algorithm.
 * Implements the main mining logic with object oriented processing capabilities
 * and advanced optimization strategies.
 * 
 * @author Meg
 * @version 1.0
 */
public class MiningEngine {
    
    private final Configuration config;
    private final PruningStrategy pruningStrategy;
    private final TopKManager topKManager;
    
    // Database and preprocessing
    private List<Transaction> database;
    private Map<Integer, Integer> itemRanking;
    private Map<Integer, Double> itemRTWU;
    
    // Performance monitoring (non-atomic for sequential processing)
    private long candidatesGenerated = 0;
    private long candidatesPruned = 0;
    private long utilityListsCreated = 0;
    private long memoryUsage = 0;
    private long startTime;
    private long endTime;
    
    /**
     * Creates a new sequential mining engine with the specified configuration.
     * 
     * @param config Mining configuration
     */
    public MiningEngine(Configuration config) {
        this.config = config;
        this.topKManager = new TopKManager(config.getK());
        this.pruningStrategy = new PruningStrategy(config);
        
        // No thread pool initialization for sequential version
        if (config.isDetailedLoggingEnabled()) {
            System.out.println("Sequential Mining Engine initialized");
        }
    }
    
    /**
     * Main mining method - executes the complete PTK-HUIM algorithm sequentially.
     * 
     * @param transactions Input transaction database
     * @return List of top-K high-utility itemsets
     */
    public List<Itemset> mine(List<Transaction> transactions) {
        startTime = System.currentTimeMillis();
        
        if (config.isDetailedLoggingEnabled()) {
            logMiningStart(transactions);
        }
        
        try {
            // Phase 1: Preprocessing and initialization
            initializeDatabase(transactions);
            
            // Phase 2: Single item processing
            Map<Integer, UtilityList> singleItemLists = processSingleItems();
            
            // Phase 3: Sequential multi-item mining
            performSequentialMining(singleItemLists);
            
            // Phase 4: Results collection
            List<Itemset> results = topKManager.getTopK();
            
            endTime = System.currentTimeMillis();
            
            if (config.isPerformanceMonitoringEnabled()) {
                logMiningResults(results);
            }
            
            return results;
            
        } finally {
            // No thread pool cleanup needed
            System.gc(); // Suggest garbage collection
        }
    }
    
    /**
     * Initializes the database and performs preprocessing.
     * Calculates RTWU values and establishes item ordering.
     * 
     * @param transactions Input transactions
     */
    private void initializeDatabase(List<Transaction> transactions) {
        if (config.isDetailedLoggingEnabled()) {
            System.out.println("Phase 1: Database initialization and preprocessing...");
        }
        
        this.database = new ArrayList<>(transactions);
        
        // Calculate RTWU for each item
        calculateRTWU();
        
        // Establish item ranking based on RTWU
        establishItemRanking();
        
        if (config.isDetailedLoggingEnabled()) {
            System.out.printf("Processed %d transactions with %d distinct items\n", 
                             database.size(), itemRTWU.size());
        }
    }
    
    /**
     * Calculates Remaining Transaction Weighted Utility (RTWU) for each item.
     * Uses single-pass algorithm for efficiency.
     */
    private void calculateRTWU() {
        itemRTWU = new HashMap<>();
        Map<Integer, Double> itemProfits = config.getItemProfits();
        
        for (Transaction transaction : database) {
            // Calculate remaining transaction utility (positive utilities only)
            double rtu = transaction.getRemainingTransactionUtility(itemProfits);
            
            // Add RTU to RTWU of each item in transaction
            for (Integer itemId : transaction.getItemIds()) {
                double probability = transaction.getProbability(itemId);
                if (probability > config.getEpsilon()) {
                    itemRTWU.merge(itemId, rtu * probability, Double::sum);
                }
            }
        }
    }
    
    /**
     * Establishes item ranking based on RTWU values.
     * Items with lower RTWU get lower rank numbers.
     */
    private void establishItemRanking() {
        itemRanking = new HashMap<>();
        
        List<Map.Entry<Integer, Double>> sortedItems = itemRTWU.entrySet().stream()
            .sorted(Map.Entry.<Integer, Double>comparingByValue()
                    .thenComparing(Map.Entry::getKey))
            .collect(Collectors.toList());
        
        for (int i = 0; i < sortedItems.size(); i++) {
            itemRanking.put(sortedItems.get(i).getKey(), i);
        }
    }
    
    /**
     * Processes single items to create initial utility lists.
     * 
     * @return Map of single-item utility lists
     */
    private Map<Integer, UtilityList> processSingleItems() {
        if (config.isDetailedLoggingEnabled()) {
            System.out.println("Phase 2: Processing single items...");
        }
        
        Map<Integer, UtilityList> singleItemLists = new HashMap<>();
        Map<Integer, Double> itemProfits = config.getItemProfits();
        
        for (Map.Entry<Integer, Double> entry : itemRTWU.entrySet()) {
            Integer itemId = entry.getKey();
            Double rtwu = entry.getValue();
            
            // Check if item can be pruned by RTWU
            if (pruningStrategy.canPruneByRTWU(rtwu, topKManager.getThreshold())) {
                continue;
            }
            
            // Create utility list for single item
            UtilityList utilityList = UtilityList.createSingleItemUtilityList(
                itemId, database, itemProfits, itemRanking
            );
            
            if (utilityList != null && !utilityList.isEmpty()) {
                utilityListsCreated++;
                
                // Check if single item qualifies for top-K
                if (!pruningStrategy.canPrune(utilityList, 
                                            topKManager.getThreshold(), 
                                            config.getMinProbability())) {
                    
                    // Add to top-K if it meets thresholds
                    if (utilityList.getSumExpectedUtility() >= topKManager.getThreshold() - config.getEpsilon() &&
                        utilityList.getExistentialProbability() >= config.getMinProbability() - config.getEpsilon()) {
                        
                        topKManager.tryAdd(utilityList.getItemset(), 
                                         utilityList.getSumExpectedUtility(),
                                         utilityList.getExistentialProbability());
                    }
                    
                    singleItemLists.put(itemId, utilityList);
                } else {
                    candidatesPruned++;
                }
            }
        }
        
        if (config.isDetailedLoggingEnabled()) {
            System.out.printf("Created %d single-item utility lists\n", singleItemLists.size());
        }
        
        return singleItemLists;
    }
    
    /**
     * Performs sequential multi-item mining using depth-first search.
     * 
     * @param singleItemLists Single-item utility lists
     */
    private void performSequentialMining(Map<Integer, UtilityList> singleItemLists) {
        if (config.isDetailedLoggingEnabled()) {
            System.out.println("Phase 3: Sequential multi-item mining...");
        }
        
        List<Map.Entry<Integer, UtilityList>> sortedItems = getSortedItemsByRank(singleItemLists);
        
        if (config.isDetailedLoggingEnabled()) {
            System.out.printf("Processing %d items sequentially\n", sortedItems.size());
        }
        
        // Process each prefix item
        for (int i = 0; i < sortedItems.size(); i++) {
            UtilityList prefixUL = sortedItems.get(i).getValue();
            
            // Get candidate extensions
            List<UtilityList> extensions = new ArrayList<>();
            for (int j = i + 1; j < sortedItems.size(); j++) {
                UtilityList extensionUL = sortedItems.get(j).getValue();
                
                // Check if extension can be pruned by RTWU
                Integer extItemId = extensionUL.getItemset().iterator().next();
                double extRTWU = itemRTWU.getOrDefault(extItemId, 0.0);
                
                if (!pruningStrategy.canPruneByRTWU(extRTWU, topKManager.getThreshold())) {
                    extensions.add(extensionUL);
                } else {
                    candidatesPruned++;
                }
            }
            
            if (!extensions.isEmpty()) {
                searchRecursive(prefixUL, extensions);
            }
            
            // Periodic progress reporting and memory monitoring
            if (i % 100 == 0 && config.isPerformanceMonitoringEnabled()) {
                monitorMemoryUsage();
                if (config.isDetailedLoggingEnabled()) {
                    System.out.printf("Progress: %d/%d items processed (%.1f%%), " +
                                    "Generated: %d, Pruned: %d, Threshold: %.6f\n", 
                                    i + 1, sortedItems.size(), 
                                    (double)(i + 1) / sortedItems.size() * 100,
                                    candidatesGenerated, candidatesPruned,
                                    topKManager.getThreshold());
                }
            }
        }
    }
    
    /**
     * Recursive search method for mining multi-item utility lists.
     * Pure sequential implementation without any parallel constructs.
     * 
     * @param prefix Current prefix utility list
     * @param extensions List of possible extensions
     */
    private void searchRecursive(UtilityList prefix, List<UtilityList> extensions) {
        if (extensions.isEmpty()) {
            return;
        }
        
        // Apply bulk pruning if enabled
        List<UtilityList> viableExtensions = extensions;
        if (config.isBulkPruningEnabled()) {
            viableExtensions = pruningStrategy.bulkPruneByRTWU(extensions, topKManager.getThreshold());
            candidatesPruned += (extensions.size() - viableExtensions.size());
        }
        
        // Process each extension sequentially
        for (int i = 0; i < viableExtensions.size(); i++) {
            UtilityList extension = viableExtensions.get(i);
            
            // Join prefix with extension
            UtilityList joined = prefix.join(extension);
            
            if (joined == null || joined.isEmpty()) {
                continue;
            }
            
            candidatesGenerated++;
            utilityListsCreated++;
            
            // Apply pruning strategies
            if (pruningStrategy.canPrune(joined, topKManager.getThreshold(), config.getMinProbability())) {
                candidatesPruned++;
                continue;
            }
            
            // Check if itemset qualifies for top-K
            double expectedUtility = joined.getSumExpectedUtility();
            double existentialProbability = joined.getExistentialProbability();
            
            if (expectedUtility >= topKManager.getThreshold() - config.getEpsilon() &&
                existentialProbability >= config.getMinProbability() - config.getEpsilon()) {
                
                topKManager.tryAdd(joined.getItemset(), expectedUtility, existentialProbability);
            }
            
            // Prepare extensions for next level (only items after current position)
            List<UtilityList> nextLevelExtensions = new ArrayList<>();
            for (int j = i + 1; j < viableExtensions.size(); j++) {
                UtilityList nextExtension = viableExtensions.get(j);
                
                // Check if next extension is viable based on current threshold
                Integer nextItemId = nextExtension.getItemset().iterator().next();
                double nextRTWU = itemRTWU.getOrDefault(nextItemId, 0.0);
                
                if (!pruningStrategy.canPruneByRTWU(nextRTWU, topKManager.getThreshold())) {
                    nextLevelExtensions.add(nextExtension);
                } else {
                    candidatesPruned++;
                }
            }
            
            // Recursive call for next level
            if (!nextLevelExtensions.isEmpty()) {
                searchRecursive(joined, nextLevelExtensions);
            }
        }
    }
    
    /**
     * Sorts items by their RTWU ranking.
     * 
     * @param singleItemLists Map of single-item utility lists
     * @return Sorted list of items by rank
     */
    private List<Map.Entry<Integer, UtilityList>> getSortedItemsByRank(Map<Integer, UtilityList> singleItemLists) {
        return singleItemLists.entrySet().stream()
            .sorted((a, b) -> {
                Integer rankA = itemRanking.get(a.getKey());
                Integer rankB = itemRanking.get(b.getKey());
                if (rankA == null && rankB == null) return 0;
                if (rankA == null) return 1;
                if (rankB == null) return -1;
                return rankA.compareTo(rankB);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Monitors memory usage and performs garbage collection if needed.
     */
    private void monitorMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        memoryUsage = Math.max(memoryUsage, usedMemory); // Track peak usage
        
        // Trigger garbage collection if memory usage is high
        if (config.isGarbageCollectionEnabled() && 
            usedMemory > config.getMaxMemoryUsage() * 0.8) {
            System.gc();
        }
    }
    
    /**
     * Logs mining start information.
     * 
     * @param transactions Input transactions
     */
    private void logMiningStart(List<Transaction> transactions) {
        System.out.println("=== PTK-HUIM Sequential Mining Engine v1.0 ===");
        System.out.printf("Database size: %d transactions\n", transactions.size());
        System.out.printf("Number of items: %d\n", config.getItemProfits().size());
        System.out.printf("K: %d, MinProbability: %.4f\n", config.getK(), config.getMinProbability());
        System.out.println("Processing mode: Sequential (single-threaded)");
        System.out.printf("Available memory: %d MB\n", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        System.out.println();
    }
    
    /**
     * Logs mining results and performance statistics.
     * 
     * @param results Mining results
     */
    private void logMiningResults(List<Itemset> results) {
        long executionTime = endTime - startTime;
        
        System.out.println("\n=== Sequential Mining Complete ===");
        System.out.printf("Execution time: %d ms\n", executionTime);
        System.out.printf("Candidates generated: %d\n", candidatesGenerated);
        System.out.printf("Candidates pruned: %d (%.2f%%)\n", 
                         candidatesPruned, 
                         candidatesGenerated > 0 ? (double)candidatesPruned / candidatesGenerated * 100 : 0);
        System.out.printf("Utility lists created: %d\n", utilityListsCreated);
        System.out.printf("Peak memory usage: %d MB\n", memoryUsage / 1024 / 1024);
        System.out.printf("Final threshold: %.6f\n", topKManager.getThreshold());
        System.out.printf("Top-K results found: %d\n", results.size());
        
        // Pruning strategy statistics
        PruningStrategy.PruningEffectiveness pruningStats = pruningStrategy.evaluateEffectiveness();
        System.out.println("\nPruning Statistics:");
        System.out.println(pruningStats.toString());
        
        // TopK manager statistics
        if (config.isPerformanceMonitoringEnabled()) {
            TopKManager.PerformanceStats topKStats = topKManager.getPerformanceStats();
            System.out.println("\nTop-K Manager Statistics:");
            System.out.println(topKStats.toString());
        }
        
        // Performance metrics
        if (executionTime > 0) {
            double candidatesPerSecond = (double)candidatesGenerated / executionTime * 1000;
            System.out.printf("Throughput: %.2f candidates/second\n", candidatesPerSecond);
        }
    }
    
    /**
     * Gets current mining statistics.
     * 
     * @return Map containing mining statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("candidatesGenerated", candidatesGenerated);
        stats.put("candidatesPruned", candidatesPruned);
        stats.put("utilityListsCreated", utilityListsCreated);
        stats.put("peakMemoryUsage", memoryUsage);
        stats.put("currentThreshold", topKManager.getThreshold());
        stats.put("topKSize", topKManager.getCurrentSize());
        stats.put("processingMode", "Sequential");
        
        if (startTime > 0) {
            long currentTime = System.currentTimeMillis();
            stats.put("elapsedTime", currentTime - startTime);
        }
        
        return stats;
    }
    
    /**
     * Exports detailed mining results including intermediate data.
     * 
     * @return Detailed mining results
     */
    public MiningResults exportDetailedResults() {
        return new MiningResults(
            topKManager.getTopK(),
            getStatistics(),
            pruningStrategy.evaluateEffectiveness(),
            topKManager.getPerformanceStats(),
            endTime - startTime
        );
    }
    
    /**
     * Detailed mining results container.
     */
    public static class MiningResults {
        private final List<Itemset> topKItemsets;
        private final Map<String, Object> generalStatistics;
        private final PruningStrategy.PruningEffectiveness pruningStats;
        private final TopKManager.PerformanceStats topKStats;
        private final long executionTime;
        
        public MiningResults(List<Itemset> topKItemsets,
                           Map<String, Object> generalStatistics,
                           PruningStrategy.PruningEffectiveness pruningStats,
                           TopKManager.PerformanceStats topKStats,
                           long executionTime) {
            this.topKItemsets = new ArrayList<>(topKItemsets);
            this.generalStatistics = new HashMap<>(generalStatistics);
            this.pruningStats = pruningStats;
            this.topKStats = topKStats;
            this.executionTime = executionTime;
        }
        
        public List<Itemset> getTopKItemsets() { return topKItemsets; }
        public Map<String, Object> getGeneralStatistics() { return generalStatistics; }
        public PruningStrategy.PruningEffectiveness getPruningStats() { return pruningStats; }
        public TopKManager.PerformanceStats getTopKStats() { return topKStats; }
        public long getExecutionTime() { return executionTime; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Sequential MiningResults{\n");
            sb.append("  executionTime: ").append(executionTime).append(" ms\n");
            sb.append("  topKItemsets: ").append(topKItemsets.size()).append("\n");
            sb.append("  processingMode: Sequential\n");
            sb.append("  pruningStats: ").append(pruningStats).append("\n");
            sb.append("  topKStats: ").append(topKStats).append("\n");
            sb.append("  generalStats: ").append(generalStatistics).append("\n");
            sb.append("}");
            return sb.toString();
        }
    }
}