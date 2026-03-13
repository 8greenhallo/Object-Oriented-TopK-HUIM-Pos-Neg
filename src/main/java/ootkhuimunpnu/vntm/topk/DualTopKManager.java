package ootkhuimunpnu.vntm.topk;

import java.util.*;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.db.Itemset;

/**
 * TreeSet-based top-K manager with O(log K) insertion.
 *
 * <p>Uses a {@link TreeSet} (sorted in descending EU order) for the result set
 * and a {@link HashMap} for O(1) duplicate detection.  All public methods are
 * {@code synchronized} to allow safe use from multi-threaded callers.
 * 
 * <h3>Data Structures</h3>
 * <pre> Tier 1 (HashMap): Provides O(1) constant-time lookup to ensure every discovered high-utility probabilistic itemset (PHUI) is unique.
 * <pre> Tier 2 (TreeSet): Maintains the results in O(log K) time, ensuring that the weakest member (the current threshold) is always accessible for pruning.
 * 
 * <h3>Threshold Semantics</h3>
 * <pre>
 *   threshold = EU of the K-th itemset (lowest in the top-K set)
 *   When |top-K| < K  →  threshold = 0.0
 * </pre>
 * Any candidate itemset with EU &lt; threshold cannot displace any existing
 * member and is pruned without further evaluation.
 *
 * @author Meg
 * @version 5.0
 * 
 * @see ITopKManager      interface this class implements
 * @see FTKTopKManager    alternative priority-queue-based implementation with O(1) insertion but O(K) threshold updates
 * @see TopHUIManager     simpler manager that maintains only confirmed HUIs (no TU seeds)
 */
public class DualTopKManager implements ITopKManager {

    /** Maximum number of itemsets to maintain. */
    private final int k;

    /**
     * TreeSet sorted by descending EU ({@link Itemset#compareTo} returns
     * negative when EU is larger). The best itemset is always at the head.
     */
    private final TreeSet<Itemset> topKSet;

    /**
     * HashMap for O(1) duplicate check: item-set → Itemset object.
     * Avoids linear scan of the TreeSet for duplicates.
     */
    private final Map<Set<Integer>, Itemset> itemsetMap;

    /** Current threshold = EU of the K-th best itemset (0.0 when |set| &lt; K). */
    private double threshold;

    /**
     * Constructs a TopKManager.
     *
     * @param k number of top itemsets to maintain
     */
    public DualTopKManager(int k) {
        this.k          = k;
        this.topKSet    = new TreeSet<>();
        this.itemsetMap = new HashMap<>();
        this.threshold  = 0.0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Fast-reject: EU &lt; threshold and |set| ≥ K.</li>
     *   <li>Duplicate check: if itemset already present and EU is better, replace it.</li>
     *   <li>Add new itemset; if |set| &gt; K, remove the lowest-EU member.</li>
     *   <li>Update threshold.</li>
     * </ol>
     */
    @Override
    public synchronized boolean tryAdd(Set<Integer> items,
                                        double expectedUtility,
                                        double existentialProbability) {
        // Fast reject: threshold is only meaningful when the set is full
        if (topKSet.size() >= k && expectedUtility < threshold - AlgorithmConfig.EPSILON) {
            return false;
        }

        // Duplicate check: update if better EU
        // Check for existing itemset
        Itemset existing = itemsetMap.get(items);
        if (existing != null) {
            // Update if better utility
            if (expectedUtility > existing.getExpectedUtility() + AlgorithmConfig.EPSILON) {
                topKSet.remove(existing);
                Itemset updated = new Itemset(items, expectedUtility, existentialProbability);
                topKSet.add(updated);
                itemsetMap.put(items, updated);
                updateThreshold();
                return true;
            }
            return false; // existing is already better or equal
        }

        // New itemset: add and trim to K
        Itemset newItemset = new Itemset(items, expectedUtility, existentialProbability);
        topKSet.add(newItemset);
        itemsetMap.put(items, newItemset);

        if (topKSet.size() > k) {
            // Remove the lowest-EU itemset (tail of the descending-ordered TreeSet)
            Itemset removed = topKSet.pollLast();
            if (removed != null) {
                itemsetMap.remove(removed.getItems());
            }
        }

        updateThreshold();
        return true;
    }

    /**
     * Updates the threshold to the EU of the K-th itemset.
     * Called after every insertion or deletion.
     */
    private void updateThreshold() {
        if (topKSet.size() >= k) {
            // Walk the TreeSet to the K-th element
            Iterator<Itemset> iter = topKSet.iterator();
            Itemset kth = null;
            for (int i = 0; i < k && iter.hasNext(); i++) {
                kth = iter.next();
            }
            if (kth != null) {
                this.threshold = kth.getExpectedUtility();
            }
        } else {
            this.threshold = 0.0;
        }
    }

    @Override
    public synchronized double getThreshold() {
        return threshold;
    }

    @Override
    public synchronized List<Itemset> getTopK() {
        List<Itemset> result = new ArrayList<>();
        Iterator<Itemset> iter = topKSet.iterator();
        for (int i = 0; i < k && iter.hasNext(); i++) {
            result.add(iter.next());
        }
        return result;
    }

    /**
     * Get current count of itemsets.
     */
    @Override
    public synchronized int size() {
        return topKSet.size();
    }

    /**
     * Check if manager is full.
     */
    @Override
    public synchronized boolean isFull() {
        return topKSet.size() >= k;
    }

    @Override
    public synchronized String toString() {
        return String.format("TopKManager{k=%d, size=%d, threshold=%.4f}",
                k, topKSet.size(), threshold);
    }
}