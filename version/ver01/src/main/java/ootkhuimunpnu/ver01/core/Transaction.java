package ootkhuimunpnu.ver01.core;

import java.util.*;

/**
 * Represents a transaction in the uncertain database with positive and negative utilities.
 * This class encapsulates all transaction-related data and operations.
 * 
 * @author Meg
 * @version 1.0
 */
public class Transaction {
    private final int transactionId;
    private final Map<Integer, Integer> items;
    private final Map<Integer, Double> probabilities;
    
    // Cached values for performance
    private Double cachedPositiveUtility;
    private Double cachedNegativeUtility;
    private Double cachedRemainingUtility;

    /**
     * Constructor for creating a new transaction.
     * 
     * @param tid Transaction ID
     * @param items Map of item IDs to quantities
     * @param probabilities Map of item IDs to probabilities
     */
    public Transaction(int tid, Map<Integer, Integer> items, Map<Integer, Double> probabilities) {
        this.transactionId = tid;
        this.items = Collections.unmodifiableMap(new HashMap<>(items));
        this.probabilities = Collections.unmodifiableMap(new HashMap<>(probabilities));
        
        // Initialize cached values as null (lazy computation)
        this.cachedPositiveUtility = null;
        this.cachedNegativeUtility = null;
        this.cachedRemainingUtility = null;
    }

    /**
     * Gets the transaction ID.
     * 
     * @return Transaction ID
     */
    public int getTransactionId() {
        return transactionId;
    }

    /**
     * Gets all items in the transaction.
     * 
     * @return Unmodifiable map of item IDs to quantities
     */
    public Map<Integer, Integer> getItems() {
        return items;
    }

    /**
     * Gets all probabilities in the transaction.
     * 
     * @return Unmodifiable map of item IDs to probabilities
     */
    public Map<Integer, Double> getProbabilities() {
        return probabilities;
    }

    /**
     * Gets the quantity of a specific item.
     * 
     * @param itemId Item ID to query
     * @return Quantity of the item, or 0 if not present
     */
    public int getQuantity(int itemId) {
        return items.getOrDefault(itemId, 0);
    }

    /**
     * Gets the probability of a specific item.
     * 
     * @param itemId Item ID to query
     * @return Probability of the item, or 0.0 if not present
     */
    public double getProbability(int itemId) {
        return probabilities.getOrDefault(itemId, 0.0);
    }

    /**
     * Checks if the transaction contains a specific item.
     * 
     * @param itemId Item ID to check
     * @return True if item is present, false otherwise
     */
    public boolean containsItem(int itemId) {
        return items.containsKey(itemId);
    }

    /**
     * Gets all item IDs in the transaction.
     * 
     * @return Set of item IDs
     */
    public Set<Integer> getItemIds() {
        return items.keySet();
    }

    /**
     * Gets the number of distinct items in the transaction.
     * 
     * @return Number of distinct items
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Calculates the positive transaction utility (PTU) with lazy caching.
     * 
     * @param itemProfits Map of item IDs to profit values
     * @return Positive transaction utility
     */
    public double getPositiveTransactionUtility(Map<Integer, Double> itemProfits) {
        if (cachedPositiveUtility == null) {
            double ptu = 0.0;
            for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
                int itemId = entry.getKey();
                int quantity = entry.getValue();
                Double profit = itemProfits.get(itemId);
                
                if (profit != null && profit > 0) {
                    ptu += profit * quantity;
                }
            }
            cachedPositiveUtility = ptu;
        }
        return cachedPositiveUtility;
    }

    /**
     * Calculates the negative transaction utility (NTU) with lazy caching.
     * 
     * @param itemProfits Map of item IDs to profit values
     * @return Negative transaction utility
     */
    public double getNegativeTransactionUtility(Map<Integer, Double> itemProfits) {
        if (cachedNegativeUtility == null) {
            double ntu = 0.0;
            for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
                int itemId = entry.getKey();
                int quantity = entry.getValue();
                Double profit = itemProfits.get(itemId);
                
                if (profit != null && profit < 0) {
                    ntu += profit * quantity;
                }
            }
            cachedNegativeUtility = ntu;
        }
        return cachedNegativeUtility;
    }

    /**
     * Calculates the remaining transaction utility (RTU) - only positive utilities.
     * 
     * @param itemProfits Map of item IDs to profit values
     * @return Remaining transaction utility
     */
    public double getRemainingTransactionUtility(Map<Integer, Double> itemProfits) {
        if (cachedRemainingUtility == null) {
            cachedRemainingUtility = getPositiveTransactionUtility(itemProfits);
        }
        return cachedRemainingUtility;
    }

    /**
     * Calculates the total transaction utility (including both positive and negative).
     * 
     * @param itemProfits Map of item IDs to profit values
     * @return Total transaction utility
     */
    public double getTotalTransactionUtility(Map<Integer, Double> itemProfits) {
        return getPositiveTransactionUtility(itemProfits) + getNegativeTransactionUtility(itemProfits);
    }

    /**
     * Filters items based on a minimum probability threshold.
     * 
     * @param minProbability Minimum probability threshold
     * @return New transaction containing only items above threshold
     */
    public Transaction filterByProbability(double minProbability) {
        Map<Integer, Integer> filteredItems = new HashMap<>();
        Map<Integer, Double> filteredProbs = new HashMap<>();
        
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            int itemId = entry.getKey();
            double prob = probabilities.getOrDefault(itemId, 0.0);
            
            if (prob >= minProbability) {
                filteredItems.put(itemId, entry.getValue());
                filteredProbs.put(itemId, prob);
            }
        }
        
        return new Transaction(transactionId, filteredItems, filteredProbs);
    }

    /**
     * Creates a transaction containing only the specified items.
     * 
     * @param itemsToKeep Set of item IDs to keep
     * @return New transaction with only specified items
     */
    public Transaction filterByItems(Set<Integer> itemsToKeep) {
        Map<Integer, Integer> filteredItems = new HashMap<>();
        Map<Integer, Double> filteredProbs = new HashMap<>();
        
        for (Integer itemId : itemsToKeep) {
            if (items.containsKey(itemId)) {
                filteredItems.put(itemId, items.get(itemId));
                filteredProbs.put(itemId, probabilities.get(itemId));
            }
        }
        
        return new Transaction(transactionId, filteredItems, filteredProbs);
    }

    /**
     * Checks if this transaction supports the given itemset (contains all items).
     * 
     * @param itemset Set of items to check
     * @return True if transaction contains all items in the itemset
     */
    public boolean supportsItemset(Set<Integer> itemset) {
        return items.keySet().containsAll(itemset);
    }

    /**
     * Calculates the probability that this transaction supports the given itemset.
     * Uses independence assumption: P(X in T) = ∏ P(item in T) for all items in X
     * 
     * @param itemset Set of items
     * @return Probability that transaction supports the itemset
     */
    public double getItemsetProbability(Set<Integer> itemset) {
        double probability = 1.0;
        
        for (Integer itemId : itemset) {
            if (!items.containsKey(itemId)) {
                return 0.0; // If any item is not in transaction, probability is 0
            }
            
            double itemProb = probabilities.getOrDefault(itemId, 0.0);
            probability *= itemProb;
            
            // Early termination if probability becomes negligible
            if (probability < 1e-10) {
                return 0.0;
            }
        }
        
        return probability;
    }

    /**
     * Calculates the utility of the given itemset in this transaction.
     * 
     * @param itemset Set of items
     * @param itemProfits Map of item IDs to profit values
     * @return Utility of the itemset in this transaction
     */
    public double getItemsetUtility(Set<Integer> itemset, Map<Integer, Double> itemProfits) {
        double utility = 0.0;
        
        for (Integer itemId : itemset) {
            if (items.containsKey(itemId)) {
                int quantity = items.get(itemId);
                Double profit = itemProfits.get(itemId);
                
                if (profit != null) {
                    utility += profit * quantity;
                }
            }
        }
        
        return utility;
    }

    /**
     * Resets cached values. Should be called if profit values change.
     */
    public void clearCache() {
        cachedPositiveUtility = null;
        cachedNegativeUtility = null;
        cachedRemainingUtility = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Transaction that = (Transaction) obj;
        return transactionId == that.transactionId &&
               Objects.equals(items, that.items) &&
               Objects.equals(probabilities, that.probabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, items, probabilities);
    }

    @Override
    public String toString() {
        return String.format("Transaction{id=%d, items=%d, avgProb=%.3f}", 
                           transactionId, 
                           items.size(), 
                           probabilities.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
    }

    /**
     * Detailed string representation for debugging.
     * 
     * @return Detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction{id=").append(transactionId).append(", items=[");
        
        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            if (!first) sb.append(", ");
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            double prob = probabilities.getOrDefault(itemId, 0.0);
            
            sb.append(itemId).append(":").append(quantity).append(":").append(String.format("%.3f", prob));
            first = false;
        }
        
        sb.append("]}");
        return sb.toString();
    }
}