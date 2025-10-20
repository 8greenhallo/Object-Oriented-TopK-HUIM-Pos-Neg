package core;

import java.util.*;

/**
 * Represents a transaction in an uncertain database with positive and negative utilities.
 * This class encapsulates all transaction-level data and provides efficient access methods.
 * 
 * @author Meg
 * @version 1.2
 */
public class Transaction {
    private final int transactionId;
    private final Map<Integer, Integer> itemQuantities;
    private final Map<Integer, Double> itemProbabilities;
    private final Map<Integer, Double> itemLogProbabilities;
    
    // Cached utilities for performance
    private final double remainingTransactionUtility;
    private final double positiveTransactionUtility;
    private final double negativeTransactionUtility;
    
    // Constants for numerical stability
    private static final double LOG_EPSILON = -700.0; // exp(-700) ≈ 0
    private static final double PROBABILITY_THRESHOLD = 1e-10;
    
    /**
     * Constructs a new Transaction with the given data.
     * 
     * @param tid The transaction ID
     * @param items Map of item to quantity
     * @param probabilities Map of item to occurrence probability
     * @param itemProfits Map of item to unit profit (can be positive or negative)
     */
    public Transaction(int tid, Map<Integer, Integer> items, 
                      Map<Integer, Double> probabilities,
                      Map<Integer, Double> itemProfits) {
        this.transactionId = tid;
        this.itemQuantities = Collections.unmodifiableMap(new HashMap<>(items));
        this.itemProbabilities = Collections.unmodifiableMap(new HashMap<>(probabilities));
        
        // Pre-compute log probabilities for numerical stability
        Map<Integer, Double> logProbs = new HashMap<>();
        for (Map.Entry<Integer, Double> entry : probabilities.entrySet()) {
            double prob = entry.getValue();
            double logProb = prob > PROBABILITY_THRESHOLD ? Math.log(prob) : LOG_EPSILON;
            logProbs.put(entry.getKey(), logProb);
        }
        this.itemLogProbabilities = Collections.unmodifiableMap(logProbs);
        
        // Pre-compute transaction utilities
        double rtu = 0.0, ptu = 0.0, ntu = 0.0;
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            Integer item = entry.getKey();
            Integer quantity = entry.getValue();
            Double profit = itemProfits.get(item);
            
            if (profit != null && quantity != null) {
                double utility = profit * quantity;
                if (profit > 0) {
                    rtu += utility;
                    ptu += utility;
                } else {
                    ntu += utility;
                }
            }
        }
        
        this.remainingTransactionUtility = rtu;
        this.positiveTransactionUtility = ptu;
        this.negativeTransactionUtility = ntu;
    }
    
    /**
     * Gets the transaction ID.
     * 
     * @return The transaction ID
     */
    public int getTransactionId() {
        return transactionId;
    }
    
    /**
     * Gets the quantity of a specific item in this transaction.
     * 
     * @param item The item ID
     * @return The quantity, or 0 if item is not present
     */
    public int getItemQuantity(int item) {
        return itemQuantities.getOrDefault(item, 0);
    }
    
    /**
     * Gets the occurrence probability of a specific item in this transaction.
     * 
     * @param item The item ID
     * @return The probability, or 0.0 if item is not present
     */
    public double getItemProbability(int item) {
        return itemProbabilities.getOrDefault(item, 0.0);
    }
    
    /**
     * Gets the log probability of a specific item for numerical stability.
     * 
     * @param item The item ID
     * @return The log probability
     */
    public double getItemLogProbability(int item) {
        return itemLogProbabilities.getOrDefault(item, LOG_EPSILON);
    }
    
    /**
     * Checks if this transaction contains a specific item.
     * 
     * @param item The item ID
     * @return true if the item is present, false otherwise
     */
    public boolean containsItem(int item) {
        return itemQuantities.containsKey(item);
    }
    
    /**
     * Checks if this transaction contains all items in the given itemset.
     * 
     * @param itemset The set of items to check
     * @return true if all items are present, false otherwise
     */
    public boolean containsItemset(Set<Integer> itemset) {
        return itemQuantities.keySet().containsAll(itemset);
    }
    
    /**
     * Gets all items in this transaction.
     * 
     * @return Unmodifiable set of item IDs
     */
    public Set<Integer> getItems() {
        return itemQuantities.keySet();
    }
    
    /**
     * Gets the remaining transaction utility (sum of positive utilities only).
     * 
     * @return The RTU value
     */
    public double getRemainingTransactionUtility() {
        return remainingTransactionUtility;
    }
    
    /**
     * Gets the positive transaction utility.
     * 
     * @return The PTU value
     */
    public double getPositiveTransactionUtility() {
        return positiveTransactionUtility;
    }
    
    /**
     * Gets the negative transaction utility.
     * 
     * @return The NTU value
     */
    public double getNegativeTransactionUtility() {
        return negativeTransactionUtility;
    }
    
    /**
     * Calculates the utility of a specific itemset in this transaction.
     * 
     * @param itemset The set of items
     * @param itemProfits Map of item to unit profit
     * @return The total utility of the itemset in this transaction
     */
    public double calculateItemsetUtility(Set<Integer> itemset, Map<Integer, Double> itemProfits) {
        double totalUtility = 0.0;
        
        for (Integer item : itemset) {
            if (containsItem(item)) {
                Double profit = itemProfits.get(item);
                if (profit != null) {
                    totalUtility += profit * getItemQuantity(item);
                }
            }
        }
        
        return totalUtility;
    }
    
    /**
     * Calculates the occurrence probability of a specific itemset in this transaction.
     * Uses log-space computation for numerical stability.
     * 
     * @param itemset The set of items
     * @return The occurrence probability of the itemset
     */
    public double calculateItemsetProbability(Set<Integer> itemset) {
        if (!containsItemset(itemset)) {
            return 0.0;
        }
        
        double logProbability = 0.0;
        
        for (Integer item : itemset) {
            double logProb = getItemLogProbability(item);
            if (logProb <= LOG_EPSILON) {
                return 0.0; // Probability is essentially zero
            }
            logProbability += logProb;
        }
        
        return Math.exp(logProbability);
    }
    
    /**
     * Calculates the log probability of a specific itemset for numerical stability.
     * 
     * @param itemset The set of items
     * @return The log occurrence probability of the itemset
     */
    public double calculateItemsetLogProbability(Set<Integer> itemset) {
        if (!containsItemset(itemset)) {
            return LOG_EPSILON;
        }
        
        double logProbability = 0.0;
        
        for (Integer item : itemset) {
            double logProb = getItemLogProbability(item);
            if (logProb <= LOG_EPSILON) {
                return LOG_EPSILON; // Probability is essentially zero
            }
            logProbability += logProb;
        }
        
        return logProbability;
    }
    
    /**
     * Creates a string representation of this transaction.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return String.format("Transaction[id=%d, items=%d, RTU=%.4f, PTU=%.4f, NTU=%.4f]",
                           transactionId, itemQuantities.size(), 
                           remainingTransactionUtility, positiveTransactionUtility, 
                           negativeTransactionUtility);
    }
    
    /**
     * Checks equality based on transaction ID.
     * 
     * @param obj The object to compare
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Transaction that = (Transaction) obj;
        return transactionId == that.transactionId;
    }
    
    /**
     * Hash code based on transaction ID.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }
}