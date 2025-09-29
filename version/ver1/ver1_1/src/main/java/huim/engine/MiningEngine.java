package engine;

import core.*;
import java.util.*;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.Duration;

/**
 * Core mining engine for OOTK-HUIM-U± algorithm.
 * This class implements the complete mining process including two-phase initialization,
 * RTWU-based ordering, and recursive search with proven pruning strategies.
 * 
 * @author Meg
 * @version 1.1
 */
public class MiningEngine {
    private final Configuration config;
    private final TopKManager topKManager;
    private final PruningStrategy pruningStrategy;
    
    // Internal data structures
    private List<Transaction> database;
    private Map<Integer, Integer> itemRanking;
    private List<Integer> rankedItems;
    
    // Statistics tracking
    private long utilityListsCreated = 0;
    private long candidatesGenerated = 0;
    private long memoryChecks = 0;
    private long peakMemoryUsage = 0;
    private Instant miningStartTime;
    private Duration totalMiningTime;
    
    /**
     * Constructs a new MiningEngine with the given configuration.
     * 
     * @param config The algorithm configuration
     */
    public MiningEngine(Configuration config) {
        this.config = config;
        this.topKManager = new TopKManager(config.getK());
        this.pruningStrategy = new PruningStrategy(config, topKManager);
    }
    
    /**
     * Main mining method that executes the complete OOTK-HUIM-U± algorithm.
     * 
     * @param rawDatabase List of transactions to mine
     * @return List of top-K high-utility itemsets
     */
    public List<Itemset> mine(List<Transaction> rawDatabase) {
        miningStartTime = Instant.now();
        
        logInfo("=== OOTK-HUIM-U± Mining Engine Started ===");
        logInfo(String.format("Database size: %d transactions", rawDatabase.size()));
        logInfo(String.format("Configuration: %s", config));
        
        try {
            // Phase 1: Two-phase initialization
            Map<Integer, UtilityList> singleItemLists = performTwoPhaseInitialization(rawDatabase);
            
            // Phase 2: Build RTWU ordering
            buildRTWUOrdering(singleItemLists);
            
            // Phase 3: Process single items
            processSingleItems(singleItemLists);
            
            // Phase 4: Recursive mining
            performRecursiveMining(singleItemLists);
            
            // Phase 5: Finalize results
            List<Itemset> results = finalizeResults();
            
            totalMiningTime = Duration.between(miningStartTime, Instant.now());
            logMiningComplete(results);
            
            return results;
            
        } catch (Exception e) {
            logError("Error during mining process", e);
            throw new RuntimeException("Mining failed", e);
        }
    }
    
    /**
     * Phase 1: Two-phase initialization with RTWU computation.
     */
    private Map<Integer, UtilityList> performTwoPhaseInitialization(List<Transaction> rawDatabase) {
        logInfo("Phase 1: Two-phase initialization...");
        
        // Pass 1: Calculate RTWU values
        Map<Integer, Double> itemRTWU = calculateRTWUValues(rawDatabase);
        
        // Pass 2: Build database and utility-lists
        Map<Integer, UtilityList> singleItemLists = buildUtilityLists(rawDatabase, itemRTWU);
        
        logInfo(String.format("Initialization complete. Single-item utility lists: %d", singleItemLists.size()));
        return singleItemLists;
    }
    
    /**
     * Calculates RTWU (Redefined Transaction Weighted Utility) for each item.
     */
    private Map<Integer, Double> calculateRTWUValues(List<Transaction> rawDatabase) {
        Map<Integer, Double> itemRTWU = new HashMap<>();
        Map<Integer, Double> itemTotalProbability = new HashMap<>();
        
        for (Transaction transaction : rawDatabase) {
            double rtu = transaction.getRemainingTransactionUtility();
            
            // Add weighted RTU to each item's RTWU
            for (Integer item : transaction.getItems()) {
                double probability = transaction.getItemProbability(item);
                if (probability > config.getEpsilon()) {
                    itemRTWU.merge(item, rtu * probability, Double::sum);
                    itemTotalProbability.merge(item, probability, Double::sum);
                }
            }
        }
        
        // Filter items that don't meet minimum probability threshold
        itemRTWU.entrySet().removeIf(entry -> {
            Double totalProb = itemTotalProbability.get(entry.getKey());
            return totalProb == null || totalProb < config.getMinProbability() - config.getEpsilon();
        });
        
        logInfo(String.format("RTWU calculated for %d items", itemRTWU.size()));
        return itemRTWU;
    }
    
    /**
     * Builds single-item utility-lists and initializes the database.
     */
    private Map<Integer, UtilityList> buildUtilityLists(List<Transaction> rawDatabase, 
                                                        Map<Integer, Double> itemRTWU) {
        this.database = new ArrayList<>(rawDatabase);
        Map<Integer, UtilityList> singleItemLists = new HashMap<>();
        
        // Create temporary item ranking for utility-list creation
        Map<Integer, Integer> tempRanking = createTemporaryRanking(itemRTWU);
        
        for (Integer item : itemRTWU.keySet()) {
            UtilityList utilityList = UtilityList.createSingleItemUtilityList(
                item, database, config.getItemProfits(), tempRanking
            );
            
            if (!utilityList.isEmpty() && 
                utilityList.getExistentialProbability() >= config.getMinProbability() - config.getEpsilon()) {
                singleItemLists.put(item, utilityList);
                utilityListsCreated++;
            }
        }
        
        return singleItemLists;
    }
    
    /**
     * Creates a temporary ranking based on RTWU values.
     */
    private Map<Integer, Integer> createTemporaryRanking(Map<Integer, Double> itemRTWU) {
        List<Map.Entry<Integer, Double>> sortedEntries = itemRTWU.entrySet().stream()
            .sorted((a, b) -> {
                int cmp = Double.compare(a.getValue(), b.getValue());
                return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
            })
            .collect(Collectors.toList());
        
        Map<Integer, Integer> ranking = new HashMap<>();
        for (int i = 0; i < sortedEntries.size(); i++) {
            ranking.put(sortedEntries.get(i).getKey(), i);
        }
        
        return ranking;
    }
    
    /**
     * Phase 2: Build RTWU ordering for efficient mining.
     */
    private void buildRTWUOrdering(Map<Integer, UtilityList> singleItemLists) {
        logInfo("Phase 2: Building RTWU ordering...");
        
        // Sort items by RTWU (ascending order for efficient pruning)
        this.rankedItems = singleItemLists.entrySet().stream()
            .sorted((a, b) -> {
                // We can use expected utility as RTWU approximation here
                double rtwuA = a.getValue().getSumExpectedUtility() + a.getValue().getSumRemainingUtility();
                double rtwuB = b.getValue().getSumExpectedUtility() + b.getValue().getSumRemainingUtility();
                
                int cmp = Double.compare(rtwuA, rtwuB);
                return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Build ranking map
        this.itemRanking = new HashMap<>();
        for (int i = 0; i < rankedItems.size(); i++) {
            itemRanking.put(rankedItems.get(i), i);
        }
        
        logInfo(String.format("RTWU ordering established for %d items", rankedItems.size()));
    }
    
    /**
     * Phase 3: Process single items for top-K candidacy.
     */
    private void processSingleItems(Map<Integer, UtilityList> singleItemLists) {
        logInfo("Phase 3: Processing single items...");
        
        int qualified = 0;
        for (UtilityList utilityList : singleItemLists.values()) {
            if (pruningStrategy.qualifiesAsHighUtility(utilityList)) {
                Itemset itemset = Itemset.fromUtilityList(utilityList);
                topKManager.tryAdd(itemset);
                qualified++;
            }
        }
        
        logInfo(String.format("Single items processed. Qualified: %d, Current threshold: %.4f", 
                            qualified, topKManager.getThreshold()));
    }
    
    /**
     * Phase 4: Recursive mining with pruning.
     */
    private void performRecursiveMining(Map<Integer, UtilityList> singleItemLists) {
        logInfo("Phase 4: Recursive mining...");
        
        List<Map.Entry<Integer, UtilityList>> sortedItems = rankedItems.stream()
            .filter(singleItemLists::containsKey)
            .map(item -> new AbstractMap.SimpleEntry<>(item, singleItemLists.get(item)))
            .collect(Collectors.toList());
        
        for (int i = 0; i < sortedItems.size(); i++) {
            UtilityList prefix = sortedItems.get(i).getValue();
            
            // Get extensions (items with higher RTWU)
            List<UtilityList> extensions = new ArrayList<>();
            for (int j = i + 1; j < sortedItems.size(); j++) {
                extensions.add(sortedItems.get(j).getValue());
            }
            
            if (!extensions.isEmpty()) {
                searchRecursive(prefix, extensions);
            }
            
            // Memory monitoring
            if (config.isMemoryMonitoringEnabled() && 
                candidatesGenerated % config.getMemoryCheckInterval() == 0) {
                monitorMemoryUsage();
            }
        }
    }
    
    /**
     * Recursive search method with pruning strategies.
     */
    private void searchRecursive(UtilityList prefix, List<UtilityList> extensions) {
        for (int i = 0; i < extensions.size(); i++) {
            UtilityList extension = extensions.get(i);
            
            // Join to create candidate
            UtilityList joined = prefix.join(extension);
            if (joined == null || joined.isEmpty()) {
                continue;
            }
            
            candidatesGenerated++;
            utilityListsCreated++;
            
            // Apply pruning strategies
            PruningStrategy.PruningResult pruningResult = pruningStrategy.evaluateForPruning(joined);
            if (pruningResult.shouldPrune()) {
                if (config.isDetailedLoggingEnabled()) {
                    logDebug(String.format("Pruned: %s - %s", joined.getItemset(), pruningResult));
                }
                continue;
            }
            
            // Check if it qualifies as high-utility
            if (pruningStrategy.qualifiesAsHighUtility(joined)) {
                Itemset itemset = Itemset.fromUtilityList(joined);
                boolean added = topKManager.tryAdd(itemset);
                
                if (config.isDetailedLoggingEnabled() && added) {
                    logDebug(String.format("Added to top-K: %s", itemset));
                }
            }
            
            // Recursive exploration
            if (i < extensions.size() - 1) {
                List<UtilityList> newExtensions = extensions.subList(i + 1, extensions.size());
                searchRecursive(joined, newExtensions);
            }
        }
    }
    
    /**
     * Phase 5: Finalize results and sort if required.
     */
    private List<Itemset> finalizeResults() {
        List<Itemset> results = topKManager.getTopK();
        
        if (config.shouldSortResultsByUtility()) {
            results.sort(Comparator.reverseOrder()); // Sort by utility descending
        }
        
        return results;
    }
    
    /**
     * Monitors memory usage and updates peak usage statistics.
     */
    private void monitorMemoryUsage() {
        memoryChecks++;
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        peakMemoryUsage = Math.max(peakMemoryUsage, usedMemory);
        
        if (usedMemory > config.getMaxMemoryUsage() * 0.9) {
            logWarning("Memory usage approaching limit: " + (usedMemory / 1024 / 1024) + " MB");
            System.gc(); // Suggest garbage collection
        }
    }
    
    /**
     * Gets comprehensive mining statistics.
     */
    public MiningStatistics getStatistics() {
        return new MiningStatistics(
            utilityListsCreated,
            candidatesGenerated,
            pruningStrategy.getStatistics(),
            topKManager.getStatistics(),
            peakMemoryUsage,
            memoryChecks,
            totalMiningTime != null ? totalMiningTime.toMillis() : 0
        );
    }
    
    /**
     * Logs mining completion summary.
     */
    private void logMiningComplete(List<Itemset> results) {
        logInfo("=== Mining Complete ===");
        logInfo(String.format("Execution time: %d ms", totalMiningTime.toMillis()));
        logInfo(String.format("Utility lists created: %d", utilityListsCreated));
        logInfo(String.format("Candidates generated: %d", candidatesGenerated));
        logInfo(String.format("Peak memory usage: %d MB", peakMemoryUsage / 1024 / 1024));
        logInfo(String.format("Final threshold: %.4f", topKManager.getThreshold()));
        logInfo(String.format("Top-K results found: %d", results.size()));
        
        if (config.shouldIncludeDetailedStatistics()) {
            logInfo("Pruning statistics: " + pruningStrategy.getStatistics());
            logInfo("Top-K statistics: " + topKManager.getStatistics());
        }
    }
    
    // Logging methods
    private void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }
    
    private void logDebug(String message) {
        if (config.isDetailedLoggingEnabled()) {
            System.out.println("[DEBUG] " + message);
        }
    }
    
    private void logWarning(String message) {
        System.out.println("[WARN] " + message);
    }
    
    private void logError(String message, Exception e) {
        System.err.println("[ERROR] " + message);
        if (e != null) {
            e.printStackTrace();
        }
    }
    
    /**
     * Comprehensive statistics about the mining process.
     */
    public static class MiningStatistics {
        private final long utilityListsCreated;
        private final long candidatesGenerated;
        private final PruningStrategy.PruningStatistics pruningStats;
        private final TopKManager.Statistics topKStats;
        private final long peakMemoryUsage;
        private final long memoryChecks;
        private final long executionTimeMs;
        
        public MiningStatistics(long utilityListsCreated, long candidatesGenerated,
                              PruningStrategy.PruningStatistics pruningStats,
                              TopKManager.Statistics topKStats,
                              long peakMemoryUsage, long memoryChecks, long executionTimeMs) {
            this.utilityListsCreated = utilityListsCreated;
            this.candidatesGenerated = candidatesGenerated;
            this.pruningStats = pruningStats;
            this.topKStats = topKStats;
            this.peakMemoryUsage = peakMemoryUsage;
            this.memoryChecks = memoryChecks;
            this.executionTimeMs = executionTimeMs;
        }
        
        // Getters
        public long getUtilityListsCreated() { return utilityListsCreated; }
        public long getCandidatesGenerated() { return candidatesGenerated; }
        public PruningStrategy.PruningStatistics getPruningStats() { return pruningStats; }
        public TopKManager.Statistics getTopKStats() { return topKStats; }
        public long getPeakMemoryUsage() { return peakMemoryUsage; }
        public long getMemoryChecks() { return memoryChecks; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        
        @Override
        public String toString() {
            return String.format("MiningStats[time=%dms, candidates=%d, utilityLists=%d, " +
                               "memory=%dMB, pruning=%.2f%%]",
                               executionTimeMs, candidatesGenerated, utilityListsCreated,
                               peakMemoryUsage / 1024 / 1024, pruningStats.getPruningRatio() * 100);
        }
    }
}