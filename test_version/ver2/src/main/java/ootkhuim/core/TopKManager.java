package core;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Thread-safe top-K manager for maintaining the best k itemsets
 */
public class TopKManager {
    private final int k;
    private final TreeSet<Itemset> topKSet;
    private final Map<Set<Integer>, Itemset> itemsetMap;
    private double threshold;

    private static final double EPSILON = 1e-10;

    public TopKManager(int k) {
        this.k = k;
        this.topKSet = new TreeSet<>();
        this.itemsetMap = new HashMap<>();
        this.threshold = 0.0;
    }

    /**
     * Try to add an itemset to the top-k collection
     * @param items the itemset items
     * @param eu expected utility
     * @param ep existential probability
     * @return true if the itemset was added or updated
     */
    public synchronized boolean tryAdd(Set<Integer> items, double eu, double ep) {
        // Quick check if this itemset can't make it to top-k
        if (eu < threshold - EPSILON && topKSet.size() >= k) {
            return false;
        }

        Itemset newItemset = new Itemset(items, eu, ep);

        // Check if we already have this itemset
        Itemset existing = itemsetMap.get(items);

        if (existing != null) {
            // Update if new utility is better
            if (existing.getExpectedUtility() < eu - EPSILON) {
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

        // Remove excess itemsets if we have more than k
        while (topKSet.size() > k) {
            Itemset removed = topKSet.pollLast();
            if (removed != null) {
                itemsetMap.remove(removed.getItems());
            }
        }

        updateThreshold();
        return true;
    }

    /**
     * Try to add an itemset from utility list
     */
    public boolean tryAdd(UtilityList ul) {
        return tryAdd(ul.getItemset(), ul.getSumEU(), ul.getExistentialProbability());
    }

    /**
     * Try to add an itemset object
     */
    public boolean tryAdd(Itemset itemset) {
        return tryAdd(itemset.getItems(), itemset.getExpectedUtility(), 
                     itemset.getExistentialProbability());
    }

    private void updateThreshold() {
        if (topKSet.size() >= k) {
            // Get the k-th itemset (lowest utility among top-k)
            Iterator<Itemset> iter = topKSet.iterator();
            Itemset kthItem = null;
            for (int i = 0; i < k && iter.hasNext(); i++) {
                kthItem = iter.next();
            }
            
            if (kthItem != null) {
                this.threshold = kthItem.getExpectedUtility();
            }
        } else {
            this.threshold = 0.0;
        }
    }

    /**
     * Get current threshold for pruning
     */
    public synchronized double getThreshold() {
        return threshold;
    }

    /**
     * Get the current top-k itemsets
     */
    public synchronized List<Itemset> getTopK() {
        return topKSet.stream()
                .limit(k)
                .collect(Collectors.toList());
    }

    /**
     * Get current size of the collection
     */
    public synchronized int size() {
        return topKSet.size();
    }

    /**
     * Check if the collection is full (has k items)
     */
    public synchronized boolean isFull() {
        return topKSet.size() >= k;
    }

    /**
     * Clear all itemsets
     */
    public synchronized void clear() {
        topKSet.clear();
        itemsetMap.clear();
        threshold = 0.0;
    }

    /**
     * Get statistics about the current state
     */
    public synchronized String getStats() {
        return String.format("TopK Manager: %d/%d items, threshold=%.4f", 
                           topKSet.size(), k, threshold);
    }
}