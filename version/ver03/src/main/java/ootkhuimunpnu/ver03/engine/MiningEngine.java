package ootkhuimunpnu.ver03.engine;

import ootkhuimunpnu.ver03.core.Itemset;
import ootkhuimunpnu.ver03.core.TopKManager;
import ootkhuimunpnu.ver03.core.Transaction;
import ootkhuimunpnu.ver03.core.Transaction.EnhancedTransaction;
import ootkhuimunpnu.ver03.core.UtilityList;
import ootkhuimunpnu.ver03.engine.PruningStrategy.ItemPair;
import ootkhuimunpnu.ver03.engine.PruningStrategy.PruningStatistics;

import java.util.*;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.Duration;

/**
 * Main mining engine for OOTK-HUIM-UN-PNU algorithm.
 * Implements the main mining logic with two-pass initialization and depth-first search.
 * 
 * Design Principles:
 * - Single Responsibility: Focuses on mining logic
 * - Template Method: Main algorithm structure with customizable steps
 * - Composition: Uses other components (TopKManager, PruningStrategy)
 * - Clean Separation: I/O and mining logic separated
 * 
 * Algorithm Overview:
 * Phase 1: Two-pass database scan
 *   - Pass 1: Calculate RTWU and build EUCS
 *   - Pass 2: Build utility lists with RTWU ordering
 * Phase 2: Depth-first mining with pruning strategies
 * 
 * MODIFICATION:
 * - Integrate pruning strategies with detailed statistics tracking
 * - EUCS uses proper joint probability (multiplication)
 * 
 * @author Meg
 * @version 3.0
 */
public class MiningEngine {
    private final Configuration config;
    private final TopKManager topKManager;
    private final PruningStrategy pruningStrategy;
    private final PruningStatistics pruningStats;

    // Database storage
    private List<EnhancedTransaction> database;

    // RTWU-based item ordering
    private Map<Integer, Integer> itemToRank;
    private Map<Integer, Double> itemRTWU;

    // EUCS structure for advanced pruning
    private Map<ItemPair, Double> eucs;

    // Reverse mapping for rank to item
    private List<Integer> rankedItems;

    // Statistics
    private long candidatesGenerated = 0;
    private long utilityListsCreated = 0;
    private long peakMemoryUsage = 0;

    public MiningEngine(Configuration config) {
        this.config = config;
        this.topKManager = new TopKManager(config.getK());
        this.pruningStrategy = new PruningStrategy(config);
        this.pruningStats = new PruningStatistics();
    }

    /**
     * Main mining method
     * Executes the complete OOTK-HUIM-UN-PNU algorithm.
     * 
     * @param rawTransactions List of input transactions
     * @return List of top-K high-utility itemsets
     */
    public List<Itemset> mine(List<Transaction> rawDatabase) {
        System.out.println("=== OOTK-HUIM-UN-PNU Mining Engine Started ===");
        System.out.println("Database size: " + rawDatabase.size());
        System.out.println("Number of items: " + config.getItemCount());
        System.out.println("K: " + config.getK() + ", MinPro: " + config.getMinProbability());
        System.out.println("Available memory: " + (Configuration.MAX_MEMORY_USAGE / 1024 / 1024) + " MB");

        Instant start = Instant.now();

        // Phase 1: Two-pass initialization with RTWU ordering
        System.out.println("\nPhase 1: Two-pass initialization with RTWU ordering...");
        Map<Integer, UtilityList> singleItemLists = twoPassInitialization(rawDatabase);

        // Sort items by RTWU for effective pruning
        List<Map.Entry<Integer, UtilityList>> sortedItems = sortItemsByRTWU(singleItemLists);

        System.out.println("Items after filtering: " + sortedItems.size());
        System.out.println("EUCS size: " + eucs.size());

        // Process single items for top-K candidacy.
        processSingleItems(sortedItems);

        System.out.println("\nPhase 2: Sequential mining with pruning strategies...");

        // Sequential mining
        sequentialMining(sortedItems, singleItemLists);

        // Finalize results and sort if required.
        List<Itemset> results = topKManager.getTopK();

        Instant end = Instant.now();

        if (config.isIncludeDetailedStatisticsEnabled()) {
            printStatistics(rawDatabase, start, end, results.size());
        }

        return results;
    }

    /**
     * Two-pass initialization with correct RTWU ordering
     */
    private Map<Integer, UtilityList> twoPassInitialization(List<Transaction> rawDatabase) {
        // PASS 1: Calculate RTWU for each item
        System.out.println("Pass 1: Computing RTWU values...");

        this.itemRTWU = new HashMap<>();
        Map<ItemPair, Double> tempEUCS = new HashMap<>();

        for (Transaction rawTrans : rawDatabase) {
            // Collect items with positive probabilities
            List<Integer> transItems = new ArrayList<>();
            Map<Integer, Double> transItemProbs = new HashMap<>();

            // Calculate RTU for this transaction (only positive utilities)
            double rtu = calculateTransactionRTU(rawTrans, transItems, transItemProbs);
            
            // Add probability-weighted RTU to RTWU of each item
            for (Integer item : transItems) {
                // Weight RTWU by item probability
                itemRTWU.merge(item, rtu * transItemProbs.get(item), Double::sum);
            }

            // Build EUCS with co-occurrence probabilities
            for (int i = 0; i < transItems.size(); i++) {
                for (int j = i + 1; j < transItems.size(); j++) {
                    ItemPair pair = new ItemPair(transItems.get(i), transItems.get(j));
                    double jointProb = transItemProbs.get(transItems.get(i)) * 
                                      transItemProbs.get(transItems.get(j));
                    tempEUCS.merge(pair, rtu * jointProb, Double::sum);
                }
            }
        }

        // Basic pruning based on RTWU (items with no positive utility)
        Set<Integer> prunedItems = filterLowRTWUItems();

        // Remove pruned items from EUCS
        this.eucs = new HashMap<>();
        for (Map.Entry<ItemPair, Double> entry : tempEUCS.entrySet()) {
            ItemPair pair = entry.getKey();
            if (!prunedItems.contains(pair.getItem1()) && 
                !prunedItems.contains(pair.getItem2())) {
                eucs.put(pair, entry.getValue());
            }
        }

        // Build global ordering based on RTWU then update itemToRank
        buildRTWUOrdering(itemRTWU);
        System.out.println("RTWU ordering established for " + itemToRank.size() + " items");

        // PASS 2: Build database with RTWU-ordered transactions and utility-lists
        System.out.println("Pass 2: Building RTWU-ordered database and utility-lists...");
        Map<Integer, UtilityList> orderedUtilityList = buildUtilityLists(rawDatabase, prunedItems);

        // Clear temporary structures to free memory
        tempEUCS.clear();

        System.gc(); // Suggest garbage collection after initialization

        return orderedUtilityList;
    }
    
    // ==================== Pass 1 Helper Methods ====================

    /**
     * Calculate RTU for a transaction and collect items with positive probabilities.
     */
    private double calculateTransactionRTU(Transaction rawTrans, List<Integer> transItems, Map<Integer, Double> transItemProbs) {
        double rtu = 0;

        for (Map.Entry<Integer, Integer> entry : rawTrans.getItems().entrySet()) {
            Integer item = entry.getKey();
            Integer quantity = entry.getValue();
            Double profit = config.getItemProfit(item);
            Double prob = rawTrans.getProbabilities().get(item);
            
            if (prob != null && prob > 0) {
                rtu += profit * quantity;
                transItems.add(item);
                transItemProbs.put(item, prob);
            }
        }
        return rtu;
    }

    /**
     * Filter items with RTWU below threshold.
     */
    private Set<Integer> filterLowRTWUItems() {
        Set<Integer> prunedItems = new HashSet<>();
        itemRTWU.entrySet().removeIf(entry -> {
            if (entry.getValue() < config.getEpsilon()) {
                prunedItems.add(entry.getKey());
                return true;
            }
            return false;
        });
        
        //System.out.println("RTWU-based pruning: " + prunedItems.size() + " items");
        return prunedItems;
    }

    /**
     * Build global item ranking based on RTWU.
     */
    private void buildRTWUOrdering(Map<Integer, Double> itemRTWU) {
        System.out.println("Building global RTWU ordering...");

        this.itemToRank = new HashMap<>();
        this.rankedItems = itemRTWU.entrySet().stream()
            .sorted((a, b) -> {
                int cmp = Double.compare(a.getValue(), b.getValue());
                if (cmp != 0) return cmp;
                return a.getKey().compareTo(b.getKey()); // Tie-break on item-id
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        for (int i = 0; i < rankedItems.size(); i++) {
            itemToRank.put(rankedItems.get(i), i);
        }

        //System.out.println("RTWU ordering established for " + itemToRank.size() + " items");
    }

    // ==================== Pass 2 Helper Methods ====================

    /**
     * Pass 2: Build utility lists with RTWU-ordered transactions.
     */
    private Map<Integer, UtilityList> buildUtilityLists(List<Transaction> rawDatabase, Set<Integer> prunedItems) {
        this.database = new ArrayList<>();
        Map<Integer, List<TempElement>> itemTempElements = new HashMap<>();

        int processedCount = 0;
        for (Transaction rawTrans : rawDatabase) {
            Map<Integer, Integer> filteredItems = new HashMap<>();
            Map<Integer, Double> filteredProbs = new HashMap<>();

            for (Map.Entry<Integer, Integer> entry : rawTrans.getItems().entrySet()) {
                Integer item = entry.getKey();
                if (!prunedItems.contains(item) && itemToRank.containsKey(item)) {
                    filteredItems.put(item, entry.getValue());
                    filteredProbs.put(item, rawTrans.getProbabilities().get(item));
                }
            }

            if (filteredItems.isEmpty()) continue;
            
            // Create enhanced transaction with RTWU ordering
            EnhancedTransaction trans = new EnhancedTransaction(
                rawTrans.getTid(), 
                rawTrans.getItems(), 
                rawTrans.getProbabilities(), 
                config.getItemProfits(), 
                itemToRank
            );
            database.add(trans);

            // Build temporary elements for each item
            for (int i = 0; i < trans.size(); i++) {
                int item = trans.getItemAt(i);

                // Only process items that have been assigned a rank
                if (!itemToRank.containsKey(item)) continue;

                double utility = config.getItemProfitOrDefault(item, 0.0) * trans.getQuantityAt(i);
                double logProb = trans.getLogProbabilityAt(i);

                // Only process items with non-negligible probability
                if (logProb > config.getLogEpsilon()) {
                    itemTempElements.computeIfAbsent(item, k -> new ArrayList<>())
                        .add(new TempElement(trans.getTid(), utility, logProb, i, trans));
                }
            }

            // Progress reporting
            if (++processedCount % config.getProgressInterval() == 0) {
                System.out.println("Processed " + processedCount + " transactions...");
            }
        }

        // Build proper utility-lists with integrated EP checking
        Map<Integer, UtilityList> singleItemLists = new HashMap<>();
        Set<Integer> epFailedItems = new HashSet<>();
        int epPrunedCount = 0;

        for (Map.Entry<Integer, List<TempElement>> entry : itemTempElements.entrySet()) {
            Integer item = entry.getKey();
            List<TempElement> tempElements = entry.getValue();

            // Build proper elements with correct remaining utility
            List<UtilityList.Element> elements = new ArrayList<>();
            for (TempElement temp : tempElements) {
                double remaining = calculateRemainingUtility(temp.trans, temp.position);

                elements.add(new UtilityList.Element(
                    temp.tid, temp.utility, remaining, temp.logProb
                ));
            }

            if (!elements.isEmpty()) {
                Set<Integer> itemset = Collections.singleton(item);
                Double rtwu = itemRTWU.get(item);

                // Create utility list - EP is calculated in constructor
                UtilityList ul = new UtilityList(itemset, elements, rtwu);

                // Check EP threshold (lazy evaluation - EP already calculated)
                if (pruningStrategy.passesExistentialProbability(ul)) {
                    singleItemLists.put(item, ul);
                    utilityListsCreated++;
                }
                else {
                    epFailedItems.add(item);
                    epPrunedCount++;
                }
            }
        }

        // Clean up EUCS for items that failed EP check
        if (!epFailedItems.isEmpty()) {
            this.eucs.entrySet().removeIf(entry ->
                epFailedItems.contains(entry.getKey().getItem1()) ||
                epFailedItems.contains(entry.getKey().getItem2())
            );
        }

        System.out.println("EP-based pruning in Pass 2: " + epPrunedCount + " items");
        System.out.println("Single item utility-lists created: " + singleItemLists.size());

        // Clear temporary structures to free memory
        itemTempElements.clear();

        return singleItemLists;
    }

    /**
     * Calculate remaining utility for a specific element
     */
    private double calculateRemainingUtility(EnhancedTransaction trans, int position) {
        double remaining = 0;
        int currentItem = trans.getItemAt(position);
        Integer currentRank = itemToRank.get(currentItem);

        if (currentRank == null) return 0;

        // Items are ordered by RTWU, so we only look at items after position
        for (int i = position + 1; i < trans.size(); i++) {
            int item = trans.getItemAt(i);
            Integer itemRank = itemToRank.get(item);

            // Since transaction is RTWU-ordered, items after position have higher RTWU
            if (itemRank != null && itemRank > currentRank) {
                Double profit = config.getItemProfit(item);
                if (profit != null && profit > 0) {
                    remaining += profit * trans.getQuantityAt(i);
                }
            }
        }

        return remaining;
    }

    /**
     * Sort items by RTWU
     */
    private List<Map.Entry<Integer, UtilityList>> sortItemsByRTWU(
            Map<Integer, UtilityList> singleItemLists) {

        return singleItemLists.entrySet().stream()
            .sorted((a, b) -> {
                // Use the pre-computed RTWU ranking
                Integer rankA = itemToRank.get(a.getKey());
                Integer rankB = itemToRank.get(b.getKey());
                if (rankA == null && rankB == null) return 0;
                if (rankA == null) return 1;
                if (rankB == null) return -1;
                return rankA.compareTo(rankB);
            })
            .collect(Collectors.toList());
    }

    /**
     * Process single items and add qualifying ones to top-k
     */
    private void processSingleItems(List<Map.Entry<Integer, UtilityList>> sortedItems) {
        System.out.println("Processing single items...");

        for (Map.Entry<Integer, UtilityList> entry : sortedItems) {
            UtilityList ul = entry.getValue();
            if (pruningStrategy.isHighUtility(ul, topKManager.getThreshold())) {
                topKManager.tryAdd(ul);
            }
        }
    }

    /**
     * Sequential mining
     */
    private void sequentialMining(List<Map.Entry<Integer, UtilityList>> sortedItems,
                                  Map<Integer, UtilityList> singleItemLists) {
        for (int i = 0; i < sortedItems.size(); i++) {
            Integer item = sortedItems.get(i).getKey();
            UtilityList ul = sortedItems.get(i).getValue();

            // Dynamic branch pruning
            if (PruningStrategy.shouldPruneByRTWU(itemRTWU.get(item), 
                                                 topKManager.getThreshold())) {
                pruningStats.recordPruning("RTWU");
                continue;
            }

            // Get extensions
            List<UtilityList> extensions = new ArrayList<>();

            for (int j = i + 1; j < sortedItems.size(); j++) {
                Integer extItem = sortedItems.get(j).getKey();
                
                // RTWU pruning
                if (PruningStrategy.shouldPruneByRTWU(itemRTWU.get(extItem), 
                                                     topKManager.getThreshold())) {
                    pruningStats.recordPruning("RTWU");
                    continue;
                }
                
                // Early EUCS pruning
                if (PruningStrategy.shouldPruneEarlyEUCS(ul.getItemset(), 
                                                        extItem, eucs,
                                                        topKManager.getThreshold())) {
                    pruningStats.recordPruning("EarlyEUCS");
                    continue;
                }
                
                extensions.add(sortedItems.get(j).getValue());

            }

            // Mine with this prefix
            if (!extensions.isEmpty()) {
                search(ul, extensions);
            }

            System.out.println("Progress: " + (i + 1) + "/" + sortedItems.size() + " items processed.");

            // Monitor memory usage
            if (config.isMemoryMonitoringEnabled() &&
                i % config.getMemoryCheckInterval() == 0 || 
                i == sortedItems.size()) {
                    
                long usedMemory = Runtime.getRuntime().totalMemory() - 
                                Runtime.getRuntime().freeMemory();
                peakMemoryUsage = Math.max(peakMemoryUsage, usedMemory);
                
                //System.out.print("[LOG] Memory check: " + (usedMemory / 1024 / 1024) + " MB.\n");

                if (usedMemory > Configuration.MAX_MEMORY_USAGE) {
                    System.out.println("[WARNING] Memory limit exceeded. Stopping mining.");
                    break;
                }
            }

        }
    }

    /**
     * Recursive search with pruning strategies
     * Explores the search space using depth-first strategy with pruning.
     * 
     * CRITICAL: EUCS pruning must ONLY be applied to the JOINED itemset,
     * not to individual extensions before joining!
     */
    private void search(UtilityList prefix, List<UtilityList> extensions) {
        double currentThreshold = topKManager.getThreshold();

        for (int i = 0; i < extensions.size(); i++) {
            UtilityList extension = extensions.get(i);

            // Pre-join pruning: Only RTWU check, do early termination if extension's RTWU is too low
            if (PruningStrategy.shouldPruneByRTWU(extension.getRtwu(), 
                                                 topKManager.getThreshold())) {
                pruningStats.recordPruning("RTWU");
                continue;
            }

            // EUCS check on COMBINED itemset (prefix + extension)
            if (PruningStrategy.shouldPruneByEUCS(prefix.getItemset(), extension.getItemset(),
                    eucs, currentThreshold)) {
                pruningStats.recordPruning("EUCS");
                continue;
            }

            // Join to create new utility-list
            UtilityList joined = UtilityList.join(prefix, extension);

            if (joined == null || joined.isEmpty()) {
                continue;
            }

            utilityListsCreated++;
            candidatesGenerated++;
            pruningStats.recordCandidate();

            double threshold = topKManager.getThreshold();

            // Apply pruning strategies
            // Post-join pruning: Probability and Expected Utility checks
            if (PruningStrategy.shouldPruneByProbability(joined, config.getMinProbability())) {
                pruningStats.recordPruning("Probability");
                continue;
            }
            
            if (PruningStrategy.shouldPruneByExpectedUtility(joined, currentThreshold)) {
                pruningStats.recordPruning("ExpectedUtility");
                continue;
            }

            // Update top-k if qualified
            if (pruningStrategy.isHighUtility(joined, threshold)) {
                topKManager.tryAdd(joined);
            }

            // Recursive search if extension potential exists
            if (pruningStrategy.hasExtensionPotential(joined, threshold) && 
                i < extensions.size() - 1) {
                List<UtilityList> newExtensions = extensions.subList(i + 1, extensions.size());
                search(joined, newExtensions);
            }
        }
    }

    /**
     * Print statistics
     */
    private void printStatistics(List<Transaction> rawDatabase, Instant start, Instant end, int resultCount) {
        System.out.println("\n=== Mining Complete ===");
        System.out.println("Total execution time: " + Duration.between(start, end).toMillis() + " ms");
        System.out.println("Candidates generated: " + candidatesGenerated);
        System.out.println("Utility lists created: " + utilityListsCreated);
        System.out.println(pruningStats.getStatistics());
        System.out.println("Peak memory usage: " + (peakMemoryUsage / 1024 / 1024) + " MB");
        System.out.println("Final threshold: " + String.format("%.4f", topKManager.getThreshold()));
        System.out.println("Top-K found: " + resultCount);
    }

    /**
     * Temporary element for two-phase processing
     */
    private static class TempElement {
        final int tid;
        final double utility;
        final double logProb;
        final int position;
        final EnhancedTransaction trans;

        TempElement(int tid, double utility, double logProb, int position, EnhancedTransaction trans) {
            this.tid = tid;
            this.utility = utility;
            this.logProb = logProb;
            this.position = position;
            this.trans = trans;
        }
    }
}