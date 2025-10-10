package engine;

import core.*;
import core.Itemset;
import core.TopKManager;
import core.Transaction;
import core.UtilityList;
import core.Transaction.*;

import java.util.*;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.Duration;

/**
 * Main mining engine for PTK-HUIM-U± algorithm
 */
public class MiningEngine {
    private final Configuration config;
    private final PruningStrategy pruningStrategy;
    private final TopKManager topKManager;

    // Algorithm state
    private List<EnhancedTransaction> database;
    private Map<Integer, Integer> itemToRank;
    private List<Integer> rankedItems;

    // Statistics
    private long candidatesGenerated = 0;
    private long utilityListsCreated = 0;
    private long peakMemoryUsage = 0;
    private final long maxMemory;

    public MiningEngine(Configuration config) {
        this.config = config;
        this.pruningStrategy = new PruningStrategy(config);
        this.topKManager = new TopKManager(config.getK());
        this.maxMemory = Runtime.getRuntime().maxMemory();
    }

    /**
     * Main mining method
     */
    public List<Itemset> mine(List<Transaction> rawDatabase) {
        System.out.println("=== PTK-HUIM-U± (Non-Parallel Version) ===");
        System.out.println("Database size: " + rawDatabase.size());
        System.out.println("Number of items: " + config.getItemCount());
        System.out.println("K: " + config.getK() + ", MinPro: " + config.getMinProbability());
        System.out.println("Available memory: " + (maxMemory / 1024 / 1024) + " MB");

        Instant start = Instant.now();

        // Two-pass initialization with RTWU ordering
        System.out.println("\nPhase 1: Two-pass initialization with RTWU ordering...");
        Map<Integer, UtilityList> singleItemLists = twoPassInitialization(rawDatabase);

        // Sort items by RTWU for effective pruning
        List<Map.Entry<Integer, UtilityList>> sortedItems = sortItemsByRTWU(singleItemLists);

        System.out.println("Items after filtering: " + sortedItems.size());

        // Process single items for top-K candidacy.
        processSingleItems(sortedItems);

        System.out.println("\nPhase 2: Sequential mining with pruning strategies...");

        // Sequential mining
        sequentialMining(sortedItems, singleItemLists);

        // Finalize results and sort if required.
        List<Itemset> results = topKManager.getTopK();

        Instant end = Instant.now();
        printStatistics(start, end, results.size());

        return results;
    }

    /**
     * Two-pass initialization with correct RTWU ordering
     */
    private Map<Integer, UtilityList> twoPassInitialization(List<Transaction> rawDatabase) {
        // PASS 1: Calculate RTWU for each item
        System.out.println("Pass 1: Computing RTWU values...");
        Map<Integer, Double> itemRTWU = new HashMap<>();
        Map<Integer, Double> itemProb = new HashMap<>();

        for (Transaction rawTrans : rawDatabase) {
            // Calculate RTU for this transaction (only positive utilities)
            double rtu = calculateTransactionRTU(rawTrans);

            // Add RTU to RTWU of each item in transaction
            for (Map.Entry<Integer, Integer> entry : rawTrans.getItems().entrySet()) {
                Integer item = entry.getKey();
                Double prob = rawTrans.getProbabilities().get(item);
                if (prob != null && prob > 0) {
                    itemRTWU.merge(item, rtu, Double::sum);
                    itemProb.merge(item, prob, Double::sum);
                }
            }
        }

        // Build global ordering based on RTWU
        buildRTWUOrdering(itemRTWU);

        // PASS 2: Build database with RTWU-ordered transactions and utility-lists
        System.out.println("Pass 2: Building RTWU-ordered database and utility-lists...");
        return buildUtilityLists(rawDatabase, itemProb);
    }

    private double calculateTransactionRTU(Transaction rawTrans) {
        double rtu = 0;
        for (Map.Entry<Integer, Integer> entry : rawTrans.getItems().entrySet()) {
            Integer item = entry.getKey();
            Integer quantity = entry.getValue();
            Double profit = config.getItemProfit(item);
            if (profit != null && profit > 0) {
                rtu += profit * quantity;
            }
        }
        return rtu;
    }

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

        System.out.println("RTWU ordering established for " + itemToRank.size() + " items");
    }

    private Map<Integer, UtilityList> buildUtilityLists(List<Transaction> rawDatabase, 
                                                        Map<Integer, Double> itemProb) {
        this.database = new ArrayList<>();
        Map<Integer, List<TempElement>> itemTempElements = new HashMap<>();

        int processedCount = 0;
        for (Transaction rawTrans : rawDatabase) {
            // Create enhanced transaction with RTWU ordering
            EnhancedTransaction trans = new EnhancedTransaction(
                rawTrans.getTid(), rawTrans.getItems(), rawTrans.getProbabilities(), 
                config.getItemProfits(), itemToRank
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
            if (++processedCount % Configuration.PROGRESS_INTERVAL == 0) {
                System.out.println("Processed " + processedCount + " transactions...");
            }
        }

        // Build proper utility-lists
        Map<Integer, UtilityList> singleItemLists = new HashMap<>();

        for (Map.Entry<Integer, List<TempElement>> entry : itemTempElements.entrySet()) {
            Integer item = entry.getKey();
            List<TempElement> tempElements = entry.getValue();

            // Check if item probability meets threshold
            Double totalProb = itemProb.get(item);
            if (totalProb == null || totalProb < config.getMinProbability()) {
                continue;
            }

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
                UtilityList ul = new UtilityList(itemset, elements);

                // Only keep if passes basic filters
                if (pruningStrategy.passesExistentialProbability(ul)) {
                    singleItemLists.put(item, ul);
                    utilityListsCreated++;
                }
            }
        }

        System.out.println("Single item utility-lists created: " + singleItemLists.size());
        System.gc(); // Suggest garbage collection after initialization

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
            UtilityList ul = sortedItems.get(i).getValue();

            // Get extensions
            List<UtilityList> extensions = new ArrayList<>();
            for (int j = i + 1; j < sortedItems.size(); j++) {
                extensions.add(sortedItems.get(j).getValue());
            }

            // Mine with this prefix
            if (!extensions.isEmpty()) {
                search(ul, extensions);
            }

            // Monitor memory usage
            if (i+1 % Configuration.DEFAULT_MEMORY_CHECK_INTERVAL == 0) {
                long usedMemory = Runtime.getRuntime().totalMemory() - 
                                 Runtime.getRuntime().freeMemory();
                peakMemoryUsage = Math.max(peakMemoryUsage, usedMemory);
                
                if (i % 50 == 0) {
                    System.out.println("Progress: " + (i + 1) + "/" + sortedItems.size() +
                                     " items processed. Memory: " + (usedMemory / 1024 / 1024) + " MB");
                }
            }
        }
    }

    /**
     * Recursive search with pruning strategies
     */
    private void search(UtilityList prefix, List<UtilityList> extensions) {
        for (int i = 0; i < extensions.size(); i++) {
            UtilityList extension = extensions.get(i);

            // Join to create new utility-list
            UtilityList joined = UtilityList.join(prefix, extension);

            if (joined == null || joined.isEmpty()) {
                continue;
            }

            utilityListsCreated++;
            candidatesGenerated++;

            double threshold = topKManager.getThreshold();

            // Apply pruning strategies
            if (pruningStrategy.shouldPrune(joined, threshold)) {
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
    private void printStatistics(Instant start, Instant end, int resultCount) {
        System.out.println("\n=== Mining Complete ===");
        System.out.println("Total execution time: " + Duration.between(start, end).toMillis() + " ms");
        System.out.println("Utility lists created: " + utilityListsCreated);
        System.out.println("Candidates generated: " + candidatesGenerated);
        System.out.println(pruningStrategy.getStatistics());
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