package ootkhuimunpnu.ver03.core;

import ootkhuimunpnu.ver03.engine.*;
import java.util.*;

/**
 * Enhanced Transaction class with efficient storage and RTWU ordering
 * 
 * Design Principles:
 * - Encapsulation: All fields are final and private with controlled access
 * - Immutability: Once created, transaction data cannot be modified
 * - Efficiency: Uses arrays for sorted storage and HashMap for O(1) lookups
 * 
 * MODIFICATIONS:
 * 
 * @author Meg
 * @version 3.0
 */
public class Transaction {
    
    /**
     * Raw transaction class for input
     */
    private final int tid;
    private final Map<Integer, Integer> items;
    private final Map<Integer, Double> probabilities;

    public Transaction(int tid, Map<Integer, Integer> items, 
                        Map<Integer, Double> probabilities) {
        this.tid = tid;
        this.items = items;
        this.probabilities = probabilities;
    }

    public int getTid() {
        return tid;
    }

    public Map<Integer, Integer> getItems() {
        return items;
    }

    public Map<Integer, Double> getProbabilities() {
        return probabilities;
    }

    /**
     * Enhanced Transaction class with efficient storage and RTWU ordering
     */
    public static class EnhancedTransaction {
        private final int tid;
        private final int[] items;                // Sorted by RTWU order
        private final int[] quantities;
        private final double[] logProbabilities;  // Store log probabilities to prevent underflow
        private final double rtu;                 // Remaining Transaction Utility (positive only)

        // HashMap for O(1) lookups instead of binary search
        private final Map<Integer, Integer> itemIndexMap;
        
        // Constructor for initial creation (before RTWU ordering is known): require 4 parameters
        public EnhancedTransaction(int tid, Map<Integer, Integer> itemMap,
                                    Map<Integer, Double> probMap, Map<Integer, Double> profits){
            this.tid = tid;
            int size = itemMap.size();
            this.items = new int[size];
            this.quantities = new int[size];
            this.logProbabilities = new double[size];
            this.itemIndexMap = new HashMap<>(size * 4 / 3); // Avoid rehashing

            int idx = 0;
            double rtu = 0;

            // Temporarily store items (will be sorted by RTWU later)
            for (Map.Entry<Integer, Integer> entry : itemMap.entrySet()) {
                Integer item = entry.getKey();
                items[idx] = item;
                quantities[idx] = entry.getValue();

                // Store log probability
                double prob = probMap.getOrDefault(item, 0.0);
                logProbabilities[idx] = prob > 0 ? Math.log(prob) : Configuration.LOG_EPSILON;
                
                // Build index map
                itemIndexMap.put(item, idx);

                // Calculate RTU (only positive utilities)
                Double profit = profits.get(item);
                if (profit != null && profit > 0) {
                    rtu += profit * quantities[idx];
                }
                idx++;
            }

            this.rtu = rtu;

        }

        // Constructor with RTWU ordering: require 5 parameters
        public EnhancedTransaction(int tid, Map<Integer, Integer> itemMap,
                                    Map<Integer, Double> probMap, Map<Integer, Double> profits,
                                    Map<Integer, Integer> itemToRank) {
            this.tid = tid;
            int size = itemMap.size();

            // Create item-rank pairs for sorting
            int[][] itemRankPairs = new int[size][2];
            int idx = 0;
            int unrankedCount = 0;

            for (Integer item : itemMap.keySet()) {
                Integer rank = itemToRank.get(item);
                itemRankPairs[idx][0] = item;
                itemRankPairs[idx][1] = (rank != null) ? rank : Integer.MAX_VALUE;
                if (rank == null) unrankedCount++;
                idx++;
            }

            // Sort by rank using primitive array sort (very fast dual-pivot quicksort)
            Arrays.sort(itemRankPairs, (a, b) -> Integer.compare(a[1], b[1]));

            // Initialize arrays with sorted order
            this.items = new int[size];
            this.quantities = new int[size];
            this.logProbabilities = new double[size];
            this.itemIndexMap = new HashMap<>(size * 4 / 3);

            double rtu = 0;

            for (idx = 0; idx < size; idx++) {
                int item = itemRankPairs[idx][0];
                items[idx] = item;
                quantities[idx] = itemMap.get(item);

                // Store log probability
                double prob = probMap.getOrDefault(item, 0.0);
                logProbabilities[idx] = prob > 0 ? Math.log(prob) : Configuration.LOG_EPSILON;

                // Build index map for O(1) lookup
                itemIndexMap.put(item, idx);

                // Calculate RTU (only positive utilities)
                Double profit = profits.get(item);
                if (profit != null && profit > 0) {
                    rtu += profit * quantities[idx];
                }
            }

            this.rtu = rtu;

            // Warning if many unranked items (for debugging)
            if (unrankedCount > size / 2) {
                System.err.println("Warning: Transaction " + tid + " has " +
                                unrankedCount + "/" + size + " unranked items");
            }
        }

        public int getTid() {
            return tid;
        }

        public int[] getItems() {
            return items.clone();
        }

        public int[] getQuantities() {
            return quantities.clone();
        }

        public double[] getLogProbabilities() {
            return logProbabilities.clone();
        }

        public double getRtu() {
            return rtu;
        }
        
        // Optimized with O(1) lookup methods using HashMap
        public int getItemIndex(int item) {
            Integer idx = itemIndexMap.get(item);
            return idx != null ? idx : -1;
        }

        public double getItemLogProbability(int item) {
            Integer idx = itemIndexMap.get(item);
            return idx != null ? logProbabilities[idx] : Configuration.LOG_EPSILON;
        }

        public double getItemProbability(int item) {
            return Math.exp(getItemLogProbability(item));
        }

        public int getItemQuantity(int item) {
            Integer idx = itemIndexMap.get(item);
            return idx != null ? quantities[idx] : 0;
        }

        public boolean containsItem(int item) {
            return itemIndexMap.containsKey(item);
        }

        // Additional utility methods
        int getItemCount() {
            return items.length;
        }

        // Debugging with RTWU ordering verification
        boolean isProperlyOrdered(Map<Integer, Integer> itemToRank) {
            for (int i = 0; i < items.length - 1; i++) {
                Integer rank1 = itemToRank.get(items[i]);
                Integer rank2 = itemToRank.get(items[i + 1]);
                if (rank1 != null && rank2 != null && rank1 > rank2) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return String.format("T%d: %d items, RTU=%.2f", tid, items.length, rtu);
        }

        public int getItemAt(int index) {
            return index >= 0 && index < items.length ? items[index] : -1;
        }

        public int getQuantityAt(int index) {
            return index >= 0 && index < quantities.length ? quantities[index] : 0;
        }

        public double getLogProbabilityAt(int index) {
            return index >= 0 && index < logProbabilities.length ? logProbabilities[index] : Configuration.LOG_EPSILON;
        }

        public int size() {
            return items.length;
        }
    }
}