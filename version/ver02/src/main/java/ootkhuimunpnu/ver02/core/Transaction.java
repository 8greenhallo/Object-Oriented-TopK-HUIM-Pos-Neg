package ootkhuimunpnu.ver02.core;

import java.util.*;

/**
 * Enhanced Transaction class with efficient storage and RTWU ordering
 * 
 * @author Meg
 * @version 2.0
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
     * Constructor with RTWU ordering
     */
    public static class EnhancedTransaction {
        private final int tid;
        private final int[] items;           // Sorted by RTWU order
        private final int[] quantities;
        private final double[] logProbabilities;  // Store log probabilities to prevent underflow
        private final double rtu;            // Remaining Transaction Utility (positive only)

        private static final double LOG_EPSILON = -700; // exp(-700) ≈ 0

        public EnhancedTransaction(int tid, Map<Integer, Integer> itemMap,
                        Map<Integer, Double> probMap, Map<Integer, Double> profits,
                        Map<Integer, Integer> itemToRank) {
            this.tid = tid;

            // Sort items by RTWU rank
            List<Integer> sortedItems = new ArrayList<>(itemMap.keySet());
            sortedItems.sort((a, b) -> {
                Integer rankA = itemToRank.get(a);
                Integer rankB = itemToRank.get(b);
                if (rankA == null && rankB == null) return 0;
                if (rankA == null) return 1;
                if (rankB == null) return -1;
                return rankA.compareTo(rankB);
            });

            // Convert to arrays for efficiency
            int size = sortedItems.size();
            this.items = new int[size];
            this.quantities = new int[size];
            this.logProbabilities = new double[size];

            int idx = 0;
            double rtu = 0;

            for (Integer item : sortedItems) {
                items[idx] = item;
                quantities[idx] = itemMap.get(item);

                // Store log probability
                double prob = probMap.getOrDefault(item, 0.0);
                logProbabilities[idx] = prob > 0 ? Math.log(prob) : LOG_EPSILON;

                Double profit = profits.get(item);
                if (profit != null) {
                    double utility = profit * quantities[idx];
                    if (profit > 0) {
                        rtu += utility;
                    }
                }
                idx++;
            }

            this.rtu = rtu;
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

        public int getItemIndex(int item) {
            return Arrays.binarySearch(items, item);
        }

        public double getItemLogProbability(int item) {
            int idx = getItemIndex(item);
            return idx >= 0 ? logProbabilities[idx] : LOG_EPSILON;
        }

        public double getItemProbability(int item) {
            return Math.exp(getItemLogProbability(item));
        }

        public int getItemQuantity(int item) {
            int idx = getItemIndex(item);
            return idx >= 0 ? quantities[idx] : 0;
        }

        public boolean containsItem(int item) {
            return getItemIndex(item) >= 0;
        }

        public int getItemAt(int index) {
            return index >= 0 && index < items.length ? items[index] : -1;
        }

        public int getQuantityAt(int index) {
            return index >= 0 && index < quantities.length ? quantities[index] : 0;
        }

        public double getLogProbabilityAt(int index) {
            return index >= 0 && index < logProbabilities.length ? logProbabilities[index] : LOG_EPSILON;
        }

        public int size() {
            return items.length;
        }
    }
}