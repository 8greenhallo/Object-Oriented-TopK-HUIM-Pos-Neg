package core;

import java.util.*;

/**
 * Thread-safe manager for maintaining the top-K high-utility itemsets
 * during the mining process. This class provides efficient insertion,
 * threshold management, and result retrieval operations.
 * 
 * @author Meg
 * @version 1.1
 */
public class TopKManager {
    private final int k;
    private final TreeSet<Itemset> topKSet;
    private final Map<Set<Integer>, Itemset> itemsetMap;
    
    private double threshold;
    
    // Constants for numerical comparisons
    private static final double EPSILON = 1e-10;
    
    /**
     * Constructs a new TopKManager for the given k value.
     * 
     * @param k The number of top itemsets to maintain
     * @throws IllegalArgumentException if k <= 0
     */
    public TopKManager(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("K must be positive");
        }
        
        this.k = k;
        this.topKSet = new TreeSet<>(); // Uses natural ordering of Itemset (by expected utility)
        this.itemsetMap = new HashMap<>();
        this.threshold = 0.0;
    }
    
    /**
     * Attempts to add an itemset to the top-K collection.
     * The itemset is added if it qualifies based on expected utility.
     * 
     * @param items The set of items
     * @param expectedUtility The expected utility value
     * @param existentialProbability The existential probability value
     * @return true if the itemset was added or updated, false otherwise
     */
    public synchronized boolean tryAdd(Set<Integer> items, double expectedUtility, 
                                      double existentialProbability) {
        double currentThreshold = threshold;
        
        // Quick rejection if utility is too low and we have enough itemsets
        if (expectedUtility < currentThreshold - EPSILON && topKSet.size() >= k) {
            return false;
        }
        
        Itemset newItemset = new Itemset(items, expectedUtility, existentialProbability);
        
        // Check if this itemset already exists
        Itemset existing = itemsetMap.get(items);
        
        if (existing != null) {
            // Update if the new one has higher utility
            if (existing.getExpectedUtility() < expectedUtility - EPSILON) {
                topKSet.remove(existing);
                topKSet.add(newItemset);
                itemsetMap.put(items, newItemset);
                updateThreshold();
                return true;
            }
            return false;
        }
        
        // Add new itemset
        topKSet.add(newItemset);
        itemsetMap.put(items, newItemset);
        
        // Remove excess itemsets if we exceed k
        while (topKSet.size() > k) {
            Itemset removed = topKSet.pollLast(); // Remove lowest utility itemset
            if (removed != null) {
                itemsetMap.remove(removed.getItems());
            }
        }
        
        updateThreshold();
        return true;
    }
    
    /**
     * Attempts to add an itemset object directly.
     * 
     * @param itemset The itemset to add
     * @return true if the itemset was added or updated, false otherwise
     */
    public boolean tryAdd(Itemset itemset) {
        return tryAdd(itemset.getItems(), itemset.getExpectedUtility(), 
                     itemset.getExistentialProbability());
    }
    
    /**
     * Updates the threshold based on the current k-th itemset.
     * This method should be called after modifications to the top-K set.
     */
    private void updateThreshold() {
        if (topKSet.size() >= k) {
            // Find the k-th itemset (lowest utility among top-k)
            Itemset kthItemset = topKSet.stream()
                .skip(k - 1)
                .findFirst()
                .orElse(null);
            
            if (kthItemset != null) {
                threshold = kthItemset.getExpectedUtility();
            }
        } else {
            threshold = 0.0;
        }
    }
    
    /**
     * Gets the current threshold (minimum utility to be in top-K).
     * 
     * @return Current threshold value
     */
    public double getThreshold() {
        return threshold;
    }
    
    /**
     * Gets the current number of itemsets in the top-K collection.
     * 
     * @return Current size
     */
    public int size() {
        return topKSet.size();
    }
    
    /**
     * Checks if the top-K collection is full.
     * 
     * @return true if the collection contains k itemsets, false otherwise
     */
    public boolean isFull() {
        return topKSet.size() >= k;
    }
    
    /**
     * Checks if the top-K collection is empty.
     * 
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return topKSet.isEmpty();
    }
    
    /**
     * Gets the target k value.
     * 
     * @return The k value
     */
    public int getK() {
        return k;
    }
    
    /**
     * Checks if a given utility value would qualify for the top-K.
     * 
     * @param expectedUtility The utility value to check
     * @return true if the utility would qualify, false otherwise
     */
    public boolean wouldQualify(double expectedUtility) {
        return !isFull() || expectedUtility >= getThreshold() - EPSILON;
    }
    
    /**
     * Gets the top-K itemsets as an ordered list.
     * The list is ordered by expected utility in descending order.
     * 
     * @return List of top-K itemsets
     */
    public List<Itemset> getTopK() {
        return new ArrayList<>(topKSet).subList(0, Math.min(k, topKSet.size()));
    }
    
    /**
     * Gets all itemsets currently in the collection.
     * This may be useful for debugging or analysis.
     * 
     * @return List of all itemsets
     */
    public List<Itemset> getAllItemsets() {
        return new ArrayList<>(topKSet);
    }
    
    /**
     * Gets the itemset with the highest expected utility.
     * 
     * @return The best itemset, or null if empty
     */
    public Itemset getBest() {
        return topKSet.isEmpty() ? null : topKSet.first();
    }
    
    /**
     * Gets the itemset with the lowest expected utility among top-K.
     * 
     * @return The worst itemset in top-K, or null if empty
     */
    public Itemset getWorst() {
        if (topKSet.size() < k) {
            return topKSet.isEmpty() ? null : topKSet.last();
        }
        return topKSet.stream().skip(k - 1).findFirst().orElse(null);
    }
    
    /**
     * Clears all itemsets from the collection.
     */
    public synchronized void clear() {
        topKSet.clear();
        itemsetMap.clear();
        threshold = 0.0;
    }
    
    /**
     * Gets statistics about the current top-K collection.
     * 
     * @return Statistics object
     */
    public Statistics getStatistics() {
        if (topKSet.isEmpty()) {
            return new Statistics(0, 0.0, 0.0, 0.0, 0.0);
        }
        
        List<Itemset> itemsets = getTopK();
        
        double minUtility = itemsets.get(itemsets.size() - 1).getExpectedUtility();
        double maxUtility = itemsets.get(0).getExpectedUtility();
        double avgUtility = itemsets.stream()
            .mapToDouble(Itemset::getExpectedUtility)
            .average()
            .orElse(0.0);
        
        double avgProbability = itemsets.stream()
            .mapToDouble(Itemset::getExistentialProbability)
            .average()
            .orElse(0.0);
        
        return new Statistics(itemsets.size(), minUtility, maxUtility, avgUtility, avgProbability);
    }
    
    /**
     * Statistics class for top-K collection analysis.
     */
    public static class Statistics {
        private final int count;
        private final double minUtility;
        private final double maxUtility;
        private final double avgUtility;
        private final double avgProbability;
        
        public Statistics(int count, double minUtility, double maxUtility, 
                         double avgUtility, double avgProbability) {
            this.count = count;
            this.minUtility = minUtility;
            this.maxUtility = maxUtility;
            this.avgUtility = avgUtility;
            this.avgProbability = avgProbability;
        }
        
        public int getCount() { return count; }
        public double getMinUtility() { return minUtility; }
        public double getMaxUtility() { return maxUtility; }
        public double getAvgUtility() { return avgUtility; }
        public double getAvgProbability() { return avgProbability; }
        
        @Override
        public String toString() {
            return String.format("Statistics[count=%d, utility=[%.4f, %.4f, %.4f], avgProb=%.4f]",
                               count, minUtility, avgUtility, maxUtility, avgProbability);
        }
    }
    
    /**
     * Creates a string representation of the current top-K collection.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return String.format("TopKManager[k=%d, size=%d, threshold=%.4f]", 
                           k, topKSet.size(), threshold);
    }
    
    /**
     * Validates the internal consistency of the top-K collection.
     * This method is primarily for debugging and testing.
     * 
     * @return true if consistent, false otherwise
     */
    public boolean validateConsistency() {
        // Check size constraint
        if (topKSet.size() > k) {
            return false;
        }
        
        // Check that itemsetMap and topKSet are synchronized
        if (topKSet.size() != itemsetMap.size()) {
            return false;
        }
        
        // Check that all itemsets in topKSet are in itemsetMap
        for (Itemset itemset : topKSet) {
            if (!itemsetMap.containsKey(itemset.getItems())) {
                return false;
            }
        }
        
        // Check ordering (itemsets should be in descending utility order)
        List<Itemset> itemsets = new ArrayList<>(topKSet);
        for (int i = 1; i < itemsets.size(); i++) {
            if (itemsets.get(i - 1).getExpectedUtility() < itemsets.get(i).getExpectedUtility() - EPSILON) {
                return false;
            }
        }
        
        // Check threshold consistency
        if (topKSet.size() >= k) {
            Itemset kthItemset = itemsets.get(k - 1);
            if (Math.abs(kthItemset.getExpectedUtility() - threshold) > EPSILON) {
                return false;
            }
        }
        
        return true;
    }
}