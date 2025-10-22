package ootkhuimunpnu.ver01.core;

import java.util.*;

/**
 * Sequential Top-K manager for maintaining the best K high-utility itemsets.
 * Optimized for single-threaded performance without synchronization overhead.
 * 
 * @author Meg
 * @version 1.0
 */
public class TopKManager {
    
    private final int k;
    private final List<Itemset> topKList;
    private final Map<Set<Integer>, Itemset> itemsetMap;
    private double threshold;
    private int currentSize;
    
    // Performance monitoring
    private long addAttempts;
    private long successfulAdds;
    private long duplicatesRejected;
    private long thresholdRejected;
    
    // Constants
    private static final double EPSILON = 1e-10;

    /**
     * Creates a new sequential TopKManager.
     * 
     * @param k Number of top itemsets to maintain
     */
    public TopKManager(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("K must be positive");
        }
        
        this.k = k;
        this.threshold = 0.0;
        this.currentSize = 0;
        
        // Simple collections for sequential processing
        this.topKList = new ArrayList<>(k + 1); // +1 for temporary overflow
        this.itemsetMap = new HashMap<>();
        
        // Initialize counters
        this.addAttempts = 0;
        this.successfulAdds = 0;
        this.duplicatesRejected = 0;
        this.thresholdRejected = 0;
    }

    /**
     * Attempts to add itemset to top-K collection.
     */
    public boolean tryAdd(Set<Integer> items, double expectedUtility, double existentialProbability) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        
        addAttempts++;
        
        // Quick threshold check
        if (expectedUtility < threshold - EPSILON && topKList.size() >= k) {
            thresholdRejected++;
            return false;
        }
        
        Itemset newItemset = new Itemset(items, expectedUtility, existentialProbability);
        
        // Check for existing itemset
        Itemset existingItemset = itemsetMap.get(items);
        if (existingItemset != null) {
            if (expectedUtility > existingItemset.getExpectedUtility() + EPSILON) {
                return updateExistingItemset(existingItemset, newItemset);
            } else {
                duplicatesRejected++;
                return false;
            }
        }
        
        // Add new itemset
        return addNewItemset(newItemset);
    }

    private boolean updateExistingItemset(Itemset oldItemset, Itemset newItemset) {
        int index = topKList.indexOf(oldItemset);
        if (index >= 0) {
            topKList.set(index, newItemset);
            itemsetMap.put(newItemset.getItems(), newItemset);
            
            // Re-sort and update threshold
            Collections.sort(topKList);
            updateThreshold();
            successfulAdds++;
            return true;
        }
        return false;
    }

    private boolean addNewItemset(Itemset newItemset) {
        // Add itemset
        topKList.add(newItemset);
        itemsetMap.put(newItemset.getItems(), newItemset);
        currentSize++;
        
        // Sort by expected utility (descending)
        Collections.sort(topKList);
        
        // Remove excess itemsets
        while (topKList.size() > k) {
            Itemset removed = topKList.remove(topKList.size() - 1);
            itemsetMap.remove(removed.getItems());
            currentSize--;
        }
        
        updateThreshold();
        successfulAdds++;
        return true;
    }

    private void updateThreshold() {
        if (topKList.size() >= k) {
            threshold = topKList.get(k - 1).getExpectedUtility();
        } else {
            threshold = 0.0;
        }
    }

    public double getThreshold() {
        return threshold;
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public int getCapacity() {
        return k;
    }

    public boolean isFull() {
        return currentSize >= k;
    }

    public List<Itemset> getTopK() {
        List<Itemset> result = new ArrayList<>(topKList);
        Collections.sort(result);
        return result.subList(0, Math.min(k, result.size()));
    }

    public List<Itemset> getTopN(int n) {
        if (n <= 0 || n > k) {
            throw new IllegalArgumentException("N must be between 1 and " + k);
        }
        
        List<Itemset> result = getTopK();
        return result.subList(0, Math.min(n, result.size()));
    }

    public boolean wouldAccept(double expectedUtility) {
        return expectedUtility >= threshold - EPSILON || currentSize < k;
    }

    public boolean containsItemset(Set<Integer> items) {
        return itemsetMap.containsKey(items);
    }

    public Itemset getItemset(Set<Integer> items) {
        return itemsetMap.get(items);
    }

    public void clear() {
        topKList.clear();
        itemsetMap.clear();
        currentSize = 0;
        threshold = 0.0;
        
        // Reset counters
        addAttempts = 0;
        successfulAdds = 0;
        duplicatesRejected = 0;
        thresholdRejected = 0;
    }

    /**
     * Gets performance statistics.
     */
    public PerformanceStats getPerformanceStats() {
        return new PerformanceStats(
            addAttempts, successfulAdds, duplicatesRejected, 
            thresholdRejected, currentSize, threshold
        );
    }

    /**
     * Performance statistics class.
     */
    public static class PerformanceStats {
        private final long addAttempts;
        private final long successfulAdds;
        private final long duplicatesRejected;
        private final long thresholdRejected;
        private final int currentSize;
        private final double currentThreshold;

        public PerformanceStats(long addAttempts, long successfulAdds, long duplicatesRejected, 
                              long thresholdRejected, int currentSize, double currentThreshold) {
            this.addAttempts = addAttempts;
            this.successfulAdds = successfulAdds;
            this.duplicatesRejected = duplicatesRejected;
            this.thresholdRejected = thresholdRejected;
            this.currentSize = currentSize;
            this.currentThreshold = currentThreshold;
        }

        public long getAddAttempts() { return addAttempts; }
        public long getSuccessfulAdds() { return successfulAdds; }
        public long getDuplicatesRejected() { return duplicatesRejected; }
        public long getThresholdRejected() { return thresholdRejected; }
        public int getCurrentSize() { return currentSize; }
        public double getCurrentThreshold() { return currentThreshold; }
        
        public double getSuccessRate() {
            return addAttempts > 0 ? (double) successfulAdds / addAttempts : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "Sequential TopK Stats{attempts=%d, successful=%d, duplicates=%d, rejected=%d, " +
                "size=%d, threshold=%.4f, successRate=%.2f%%}",
                addAttempts, successfulAdds, duplicatesRejected, thresholdRejected, 
                currentSize, currentThreshold, getSuccessRate() * 100
            );
        }
    }

    @Override
    public String toString() {
        return String.format("Sequential TopKManager{k=%d, size=%d, threshold=%.4f}", 
                           k, currentSize, threshold);
    }
}