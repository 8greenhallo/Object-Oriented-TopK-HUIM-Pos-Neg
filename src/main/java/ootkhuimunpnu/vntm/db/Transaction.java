package ootkhuimunpnu.vntm.db;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;

/**
 * Optimized, immutable transaction with array-based storage and PTWU-ordered items.
 *
 * <p>Items are sorted in ascending order of their PTWU rank so that the
 * <em>suffix sum</em> preprocessing (see {@link preprocessing.SuffixSumComputer})
 * can compute the <em>remaining positive utility</em> for each position in O(n) time
 * instead of the naive O(n²) approach.
 *
 * <p>Probabilities are stored as <em>log-probabilities</em> to prevent numerical
 * underflow when multiplying many small values:
 * <pre>
 *   logP(item, T) = log( P(item, T) )
 * </pre>
 * Multiplication in probability space becomes addition in log space.
 *
 * <p><b>PTU (Positive Transaction Utility)</b> captures the sum of utilities for
 * all positively-profitable items in this transaction:
 * <pre>
 *   PTU(T) = Σ{ profit(i) * qty(i, T)  |  profit(i) > 0, i ∈ T }
 * </pre>
 * PTU is the building block for PTWU (Positive Transaction-Weighted Utility)
 * used to upper-bound the expected utility of any itemset containing item i.
 *
 * @author Meg
 * @version 5.0
 */
public class Transaction {

    /** Transaction identifier. */
    private final int tid;

    /**
     * Items sorted by ascending PTWU rank.
     * Rank 0 = lowest PTWU (pruned earliest); higher ranks have more utility potential.
     */
    private final int[] items;

    /** Corresponding quantities for each item. */
    private final int[] quantities;

    /**
     * Log-probabilities: logProbabilities[i] = log( P(items[i], T) ).
     * Stored in log space to prevent floating-point underflow for very small probabilities.
     * Values below {@link AlgorithmConfig#LOG_EPSILON} are clamped to that sentinel.
     */
    private final double[] logProbabilities;

    /**
     * Positive Transaction Utility:
     * PTU(T) = Σ{ profit(i) * qty(i,T)  |  profit(i) > 0 }
     * Used as the base value for computing PTWU of individual items.
     */
    private final double ptu;

    /**
     * Index map for O(1) item lookup:  item_id → index in the {@code items} array.
     * Avoids binary search on sorted arrays.
     */
    private final Map<Integer, Integer> itemIndexMap;

    /**
     * Constructs a optimized Transaction.
     *
     * @param tid             transaction ID
     * @param items           sorted item IDs (by PTWU rank)
     * @param quantities      quantities array
     * @param logProbabilities log-probability array
     * @param ptu             positive transaction utility
     * @param itemIndexMap    pre-built item → index map
     */
    public Transaction(int tid,
                       int[] items,
                       int[] quantities,
                       double[] logProbabilities,
                       double ptu,
                       Map<Integer, Integer> itemIndexMap) {
        this.tid = tid;
        this.items = items;
        this.quantities = quantities;
        this.logProbabilities = logProbabilities;
        this.ptu = ptu;
        this.itemIndexMap = itemIndexMap;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return transaction ID */
    public int getTid() { return tid; }

    /** @return sorted item-ID array (by PTWU rank, ascending) */
    public int[] getItems() { return items; }

    /** @return quantity array */
    public int[] getQuantities() { return quantities; }

    /** @return log-probability array */
    public double[] getLogProbabilities() { return logProbabilities; }

    /** @return positive transaction utility PTU(T) */
    public double getPtu() { return ptu; }

    /** @return number of items in this transaction */
    public int size() { return items.length; }

    /**
     * Returns the item at a given position.
     *
     * @param index position (0-based)
     * @return item ID
     */
    public int getItem(int index) { return items[index]; }

    /**
     * Returns the quantity of the item at position {@code index}.
     *
     * @param index position (0-based)
     * @return quantity
     */
    public int getQuantity(int index) { return quantities[index]; }

    /**
     * Returns the log-probability of the item at position {@code index}.
     *
     * @param index position (0-based)
     * @return log-probability (≤ 0)
     */
    public double getLogProbability(int index) { return logProbabilities[index]; }

    /**
     * Checks whether the given item appears in this transaction.
     *
     * @param item item ID
     * @return {@code true} if present
     */
    public boolean containsItem(int item) { return itemIndexMap.containsKey(item); }

    /**
     * Returns the index of the given item in the sorted arrays, or −1 if absent.
     *
     * @param item item ID
     * @return array index, or −1
     */
    public int getItemIndex(int item) {
        return itemIndexMap.getOrDefault(item, -1);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Builder that constructs a Transaction from raw maps and a PTWU rank ordering.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Collect all items present in both {@code itemMap} and {@code itemToRank}.</li>
     *   <li>Sort by rank (ascending PTWU order).</li>
     *   <li>Compute PTU by summing positive utilities.</li>
     *   <li>Store log-probabilities for each item.</li>
     * </ol>
     */
    public static class Builder {

        private final int tid;
        private final Map<Integer, Integer> itemMap;        // itemMap (item → quantity):
        private final Map<Integer, Double> probabilityMap;  // probabilityMap (item → existential probability)
        private final Map<Integer, Double> profitTable;     // profitTable (item → profit value, may be negative):
        private final Map<Integer, Integer> itemToRank;     // itemToRank (item → PTWU rank)

        /**
         * Builder for creating optimized Transaction instances.
         * 
         * @param tid            transaction ID
         * @param itemMap        item ID → quantity
         * @param probabilityMap item ID → existential probability
         * @param profitTable    item ID → profit value (may be negative)
         * @param itemToRank     item ID → PTWU rank (ascending)
         */
        public Builder(int tid,
                       Map<Integer, Integer> itemMap,
                       Map<Integer, Double> probabilityMap,
                       Map<Integer, Double> profitTable,
                       Map<Integer, Integer> itemToRank) {
            this.tid = tid;
            this.itemMap = itemMap;
            this.probabilityMap = probabilityMap;
            this.profitTable = profitTable;
            this.itemToRank = itemToRank;
        }

        /**
         * Builds the optimized Transaction.
         *
         * @return a new {@link Transaction} instance
         */
        public Transaction build() {
            int n = itemMap.size();
            if (n == 0) {
                return new Transaction(tid, 
                                        new int[0], 
                                        new int[0], 
                                        new double[0],
                                        0.0, new HashMap<>());
            }

            // --- Step 1: collect (item, rank) pairs and sort by rank (fast dual-pivot quicksort) ---
            
            // Create item-rank pairs for sorting
            int[][] itemRankPairs = new int[n][2];
            int idx = 0;
            for (Integer item : itemMap.keySet()) {
                Integer rank = itemToRank.get(item);
                itemRankPairs[idx][0] = item;
                // Items not in the rank map go last 
                // (they will be filtered later, but kept here for safety)
                itemRankPairs[idx][1] = (rank != null) ? rank : Integer.MAX_VALUE;
                idx++;
            }
            // Sort items by PTWU rank using primitive array sort 
            // (very fast dual-pivot quicksort)
            Arrays.sort(itemRankPairs, 
                (a, b) -> Integer.compare(a[1], b[1]));

            // --- Step 2: populate arrays ---

            // Convert to arrays for efficiency
            int[] items = new int[n];
            int[] quantities = new int[n];
            double[] logProbabilities = new double[n];
            // Pre-sized map 
            // (load factor 0.75 → capacity = n / 0.75 + 1)
            Map<Integer, Integer> itemIndexMap = new HashMap<>((int) (n / 0.75) + 1, 0.75f);
            
            double ptu = 0.0;

            // Fill arrays + compute PTU
            for (idx = 0; idx < n; idx++) {
                int item = itemRankPairs[idx][0];
                items[idx] = item;
                quantities[idx] = itemMap.get(item);

                // Calculate Log-probability for numerical stability
                double prob = probabilityMap.getOrDefault(item, 0.0);
                if (prob < AlgorithmConfig.EPSILON) {
                    logProbabilities[idx] = AlgorithmConfig.LOG_EPSILON;
                } else if (prob > 1.0 - AlgorithmConfig.EPSILON) {
                    logProbabilities[idx] = 0.0;          // log(1) = 0
                } else {
                    logProbabilities[idx] = Math.log(prob);
                }

                itemIndexMap.put(item, idx);
                
                // Calculate PTU (positive utilities only)
                // PTU(T) = Σ{ profit(i)*qty(i,T) | profit(i) > 0 }
                Double profit = profitTable.get(item);
                if (profit != null && profit > AlgorithmConfig.EPSILON) {
                    ptu += profit * quantities[idx];
                }
            }

            return new Transaction(tid, 
                                    items, 
                                    quantities, 
                                    logProbabilities, 
                                    ptu, 
                                    itemIndexMap);
        }
    }

    @Override
    public String toString() {
        return String.format("Transaction{tid=%d, items=%d, PTU=%.2f}", tid, items.length, ptu);
    }
}