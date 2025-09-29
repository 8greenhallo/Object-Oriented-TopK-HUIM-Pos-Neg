package core;

import java.util.*;

/**
 * Enhanced Utility-List structure for efficient mining of high-utility itemsets
 * from uncertain databases. This class encapsulates all utility and probability
 * information for a specific itemset across all transactions.
 * 
 * @author Meg
 * @version 1.1
 */
public class UtilityList {
    
    /**
     * Represents a single element in the utility-list, corresponding to
     * one transaction where the itemset appears.
     */
    public static class Element {
        private final int transactionId;
        private final double utility;
        private final double remainingUtility;
        private final double logProbability;
        
        /**
         * Constructs a new utility-list element.
         * 
         * @param tid Transaction ID
         * @param utility Actual utility of the itemset in this transaction
         * @param remainingUtility Remaining positive utility for extensions
         * @param logProbability Log probability for numerical stability
         */
        public Element(int tid, double utility, double remainingUtility, double logProbability) {
            this.transactionId = tid;
            this.utility = utility;
            this.remainingUtility = remainingUtility;
            this.logProbability = logProbability;
        }
        
        public int getTransactionId() { return transactionId; }
        public double getUtility() { return utility; }
        public double getRemainingUtility() { return remainingUtility; }
        public double getLogProbability() { return logProbability; }
        
        public double getProbability() {
            return Math.exp(logProbability);
        }
        
        @Override
        public String toString() {
            return String.format("Element[tid=%d, u=%.4f, r=%.4f, p=%.4f]",
                               transactionId, utility, remainingUtility, getProbability());
        }
    }
    
    // Constants for numerical stability
    private static final double EPSILON = 1e-10;
    private static final double LOG_EPSILON = -700.0;
    
    private final Set<Integer> itemset;
    private final List<Element> elements;
    private final double sumExpectedUtility;
    private final double sumRemainingUtility;
    private final double existentialProbability;
    
    /**
     * Constructs an empty utility-list for the given itemset.
     * 
     * @param itemset The itemset this utility-list represents
     */
    public UtilityList(Set<Integer> itemset) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.elements = new ArrayList<>();
        this.sumExpectedUtility = 0.0;
        this.sumRemainingUtility = 0.0;
        this.existentialProbability = 0.0;
    }
    
    /**
     * Constructs a utility-list with the given elements.
     * 
     * @param itemset The itemset this utility-list represents
     * @param elements List of utility-list elements
     */
    public UtilityList(Set<Integer> itemset, List<Element> elements) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
        
        // Calculate aggregated values
        double expectedUtility = 0.0;
        double remainingUtility = 0.0;
        
        for (Element element : elements) {
            double probability = Math.exp(element.logProbability);
            expectedUtility += element.utility * probability;
            remainingUtility += element.remainingUtility * probability;
        }
        
        this.sumExpectedUtility = expectedUtility;
        this.sumRemainingUtility = remainingUtility;
        this.existentialProbability = calculateExistentialProbability();
    }
    
    /**
     * Gets the itemset represented by this utility-list.
     * 
     * @return Unmodifiable set of item IDs
     */
    public Set<Integer> getItemset() {
        return itemset;
    }
    
    /**
     * Gets all elements in this utility-list.
     * 
     * @return Unmodifiable list of elements
     */
    public List<Element> getElements() {
        return elements;
    }
    
    /**
     * Gets the sum of expected utilities across all transactions.
     * 
     * @return Sum of expected utilities
     */
    public double getSumExpectedUtility() {
        return sumExpectedUtility;
    }
    
    /**
     * Gets the sum of remaining utilities for pruning purposes.
     * 
     * @return Sum of remaining utilities
     */
    public double getSumRemainingUtility() {
        return sumRemainingUtility;
    }
    
    /**
     * Gets the existential probability of this itemset.
     * 
     * @return Existential probability
     */
    public double getExistentialProbability() {
        return existentialProbability;
    }
    
    /**
     * Checks if this utility-list is empty.
     * 
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }
    
    /**
     * Gets the number of elements (transactions) in this utility-list.
     * 
     * @return Number of elements
     */
    public int size() {
        return elements.size();
    }
    
    /**
     * Calculates the existential probability using log-space arithmetic
     * for numerical stability. EP(X) = 1 - ∏(T∈D, X⊆T) [1 - P(X,T)]
     * 
     * @return The existential probability
     */
    private double calculateExistentialProbability() {
        if (elements.isEmpty()) {
            return 0.0;
        }
        
        // Use log-space to avoid underflow
        double logComplement = 0.0;
        
        for (Element element : elements) {
            if (element.logProbability > Math.log(1.0 - EPSILON)) {
                // Probability is essentially 1, so EP = 1
                return 1.0;
            }
            
            // Compute log(1 - P) stably
            double probability = Math.exp(element.logProbability);
            double log1MinusP;
            
            if (probability < 0.5) {
                log1MinusP = Math.log1p(-probability);
            } else {
                log1MinusP = Math.log(1.0 - probability);
            }
            
            logComplement += log1MinusP;
            
            // Early termination if complement becomes negligible
            if (logComplement < LOG_EPSILON) {
                return 1.0;
            }
        }
        
        // Convert back: EP = 1 - exp(logComplement)
        if (logComplement < LOG_EPSILON) {
            return 1.0;
        }
        
        double complement = Math.exp(logComplement);
        return 1.0 - complement;
    }
    
    /**
     * Joins two utility-lists to create a new utility-list for the union itemset.
     * This is used during the mining process to generate candidate itemsets.
     * 
     * @param other The other utility-list to join with
     * @return New utility-list representing the union of itemsets, or null if empty
     */
    public UtilityList join(UtilityList other) {
        if (other == null) {
            return null;
        }
        
        // Create union itemset
        Set<Integer> newItemset = new HashSet<>(this.itemset);
        newItemset.addAll(other.itemset);
        
        // Perform join operation on elements
        List<Element> joinedElements = new ArrayList<>();
        
        int i = 0, j = 0;
        while (i < this.elements.size() && j < other.elements.size()) {
            Element e1 = this.elements.get(i);
            Element e2 = other.elements.get(j);
            
            if (e1.transactionId == e2.transactionId) {
                // Same transaction - combine utilities and probabilities
                double combinedUtility = e1.utility + e2.utility;
                double combinedRemainingUtility = Math.min(e1.remainingUtility, e2.remainingUtility);
                double combinedLogProbability = e1.logProbability + e2.logProbability;
                
                // Only add if probability is significant
                if (combinedLogProbability > LOG_EPSILON) {
                    joinedElements.add(new Element(
                        e1.transactionId, combinedUtility, 
                        combinedRemainingUtility, combinedLogProbability
                    ));
                }
                
                i++;
                j++;
            } else if (e1.transactionId < e2.transactionId) {
                i++;
            } else {
                j++;
            }
        }
        
        if (joinedElements.isEmpty()) {
            return null;
        }
        
        return new UtilityList(newItemset, joinedElements);
    }
    
    /**
     * Creates a utility-list for a single item from the transaction database.
     * 
     * @param item The item ID
     * @param database List of transactions
     * @param itemProfits Map of item to unit profit
     * @param itemRanking Map for RTWU-based remaining utility calculation
     * @return Utility-list for the single item
     */
    public static UtilityList createSingleItemUtilityList(int item, 
                                                          List<Transaction> database,
                                                          Map<Integer, Double> itemProfits,
                                                          Map<Integer, Integer> itemRanking) {
        List<Element> elements = new ArrayList<>();
        Set<Integer> itemset = Collections.singleton(item);
        Double profit = itemProfits.get(item);
        
        if (profit == null) {
            return new UtilityList(itemset);
        }
        
        for (Transaction transaction : database) {
            if (transaction.containsItem(item)) {
                int quantity = transaction.getItemQuantity(item);
                double utility = profit * quantity;
                double logProbability = transaction.getItemLogProbability(item);
                
                // Calculate remaining utility based on RTWU ordering
                double remainingUtility = calculateRemainingUtility(
                    item, transaction, itemProfits, itemRanking
                );
                
                if (logProbability > LOG_EPSILON) {
                    elements.add(new Element(
                        transaction.getTransactionId(), utility, 
                        remainingUtility, logProbability
                    ));
                }
            }
        }
        
        return new UtilityList(itemset, elements);
    }
    
    /**
     * Calculates the remaining positive utility for pruning purposes.
     * 
     * @param item Current item
     * @param transaction Current transaction
     * @param itemProfits Map of item to unit profit
     * @param itemRanking RTWU-based ranking for ordering
     * @return Remaining positive utility
     */
    private static double calculateRemainingUtility(int item, Transaction transaction,
                                                   Map<Integer, Double> itemProfits,
                                                   Map<Integer, Integer> itemRanking) {
        double remainingUtility = 0.0;
        Integer currentRank = itemRanking.get(item);
        
        if (currentRank == null) {
            return 0.0;
        }
        
        // Sum utilities of items with higher RTWU rank
        for (Integer otherItem : transaction.getItems()) {
            Integer otherRank = itemRanking.get(otherItem);
            if (otherRank != null && otherRank > currentRank) {
                Double otherProfit = itemProfits.get(otherItem);
                if (otherProfit != null && otherProfit > 0) {
                    remainingUtility += otherProfit * transaction.getItemQuantity(otherItem);
                }
            }
        }
        
        return remainingUtility;
    }
    
    /**
     * Creates a string representation of this utility-list.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return String.format("UtilityList[itemset=%s, size=%d, EU=%.4f, EP=%.4f]",
                           itemset, elements.size(), sumExpectedUtility, existentialProbability);
    }
}