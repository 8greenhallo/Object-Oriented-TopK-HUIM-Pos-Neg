package ootkhuimunpnu.ver01.core;

import java.util.*;

/**
 * Enhanced Utility-List with log-space probability tracking for numerical stability.
 * Implements the core data structure for efficient Top-K High-Utility Itemset Mining
 * in uncertain databases with positive and negative utilities.
 * 
 * @author Meg
 * @version 1.0
 */
public class UtilityList {
    
    /**
     * Represents an element in the utility list for a specific transaction.
     */
    public static class Element {
        private final int transactionId;
        private final double utility;
        private final double remainingUtility;
        private final double logProbability;
        
        // Constants for numerical stability
        private static final double LOG_EPSILON = -700; // exp(-700) ≈ 0
        private static final double EPSILON = 1e-10;

        /**
         * Creates a new utility list element.
         * 
         * @param tid Transaction ID
         * @param utility Actual utility in this transaction
         * @param remaining Remaining positive utility for extensions
         * @param logProbability Log probability for numerical stability
         */
        public Element(int tid, double utility, double remaining, double logProbability) {
            this.transactionId = tid;
            this.utility = utility;
            this.remainingUtility = remaining;
            this.logProbability = Math.max(logProbability, LOG_EPSILON);
        }

        // Getters
        public int getTransactionId() { return transactionId; }
        public double getUtility() { return utility; }
        public double getRemainingUtility() { return remainingUtility; }
        public double getLogProbability() { return logProbability; }
        
        /**
         * Gets the probability (exponential of log probability).
         * 
         * @return Probability value
         */
        public double getProbability() {
            return logProbability > LOG_EPSILON ? Math.exp(logProbability) : 0.0;
        }

        /**
         * Checks if this element has negligible probability.
         * 
         * @return True if probability is negligible
         */
        public boolean hasNegligibleProbability() {
            return logProbability <= LOG_EPSILON;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            
            Element element = (Element) obj;
            return transactionId == element.transactionId &&
                   Double.compare(element.utility, utility) == 0 &&
                   Double.compare(element.remainingUtility, remainingUtility) == 0 &&
                   Double.compare(element.logProbability, logProbability) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(transactionId, utility, remainingUtility, logProbability);
        }

        @Override
        public String toString() {
            return String.format("Element{tid=%d, u=%.2f, rem=%.2f, prob=%.4f}", 
                               transactionId, utility, remainingUtility, getProbability());
        }
    }

    // Core data
    private final Set<Integer> itemset;
    private final List<Element> elements;
    
    // Pre-computed aggregates for O(1) access
    private final double sumExpectedUtility;
    private final double sumRemainingUtility;
    private final double existentialProbability;
    
    // Constants
    private static final double LOG_EPSILON = -700;
    private static final double EPSILON = 1e-10;

    /**
     * Creates an empty utility list for the given itemset.
     * 
     * @param itemset Set of items this utility list represents
     */
    public UtilityList(Set<Integer> itemset) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.elements = new ArrayList<>();
        this.sumExpectedUtility = 0.0;
        this.sumRemainingUtility = 0.0;
        this.existentialProbability = 0.0;
    }

    /**
     * Creates a utility list with the given elements and pre-computes aggregates.
     * 
     * @param itemset Set of items this utility list represents
     * @param elements List of utility list elements
     */
    public UtilityList(Set<Integer> itemset, List<Element> elements) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
        
        // Pre-compute aggregates for O(1) access
        double eu = 0.0;
        double rem = 0.0;
        
        for (Element element : elements) {
            if (!element.hasNegligibleProbability()) {
                double prob = element.getProbability();
                eu += element.utility * prob;
                rem += element.remainingUtility * prob;
            }
        }
        
        this.sumExpectedUtility = eu;
        this.sumRemainingUtility = rem;
        this.existentialProbability = calculateExistentialProbability();
    }

    /**
     * Gets the itemset represented by this utility list.
     * 
     * @return Unmodifiable set of item IDs
     */
    public Set<Integer> getItemset() {
        return itemset;
    }

    /**
     * Gets all elements in this utility list.
     * 
     * @return Unmodifiable list of elements
     */
    public List<Element> getElements() {
        return elements;
    }

    /**
     * Gets the sum of expected utilities (pre-computed for O(1) access).
     * 
     * @return Sum of expected utilities
     */
    public double getSumExpectedUtility() {
        return sumExpectedUtility;
    }

    /**
     * Gets the sum of remaining utilities (pre-computed for O(1) access).
     * 
     * @return Sum of remaining utilities
     */
    public double getSumRemainingUtility() {
        return sumRemainingUtility;
    }

    /**
     * Gets the existential probability (pre-computed for O(1) access).
     * 
     * @return Existential probability
     */
    public double getExistentialProbability() {
        return existentialProbability;
    }

    /**
     * Gets the number of elements in this utility list.
     * 
     * @return Number of elements
     */
    public int size() {
        return elements.size();
    }

    /**
     * Checks if this utility list is empty.
     * 
     * @return True if empty, false otherwise
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * Gets the size of the itemset.
     * 
     * @return Size of the itemset
     */
    public int getItemsetSize() {
        return itemset.size();
    }

    /**
     * Calculates existential probability using log-space for numerical stability.
     * EP(X) = 1 - ∏(T∈D, X⊆T) [1 - P(X,T)]
     * 
     * @return Existential probability
     */
    private double calculateExistentialProbability() {
        if (elements.isEmpty()) {
            return 0.0;
        }

        // Calculate in log-space for numerical stability
        double logComplement = 0.0;
        boolean hasSignificantProbability = false;

        for (Element element : elements) {
            if (element.hasNegligibleProbability()) {
                continue;
            }

            double prob = element.getProbability();
            
            // Check if probability is essentially 1
            if (prob > 1.0 - EPSILON) {
                return 1.0;
            }

            // Compute log(1 - P) using log1p for better numerical stability
            double log1MinusP;
            if (prob < 0.5) {
                log1MinusP = Math.log1p(-prob);
            } else {
                log1MinusP = Math.log(1.0 - prob);
            }

            logComplement += log1MinusP;
            hasSignificantProbability = true;

            // Early termination if complement becomes negligible
            if (logComplement < LOG_EPSILON) {
                return 1.0;
            }
        }

        if (!hasSignificantProbability) {
            return 0.0;
        }

        // Convert back: EP = 1 - exp(logComplement)
        if (logComplement < LOG_EPSILON) {
            return 1.0;
        }

        double complement = Math.exp(logComplement);
        return Math.max(0.0, 1.0 - complement);
    }

    /**
     * Joins this utility list with another to create a new utility list.
     * This is a core operation in the mining algorithm.
     * 
     * @param other The utility list to join with
     * @return New utility list representing the union of itemsets, or null if join fails
     */
    public UtilityList join(UtilityList other) {
        if (other == null) {
            return null;
        }

        // Create union of itemsets
        Set<Integer> newItemset = new HashSet<>(this.itemset);
        newItemset.addAll(other.itemset);

        // Perform intersection of elements based on transaction IDs
        List<Element> joinedElements = new ArrayList<>();
        
        int i = 0, j = 0;
        while (i < this.elements.size() && j < other.elements.size()) {
            Element e1 = this.elements.get(i);
            Element e2 = other.elements.get(j);

            if (e1.transactionId == e2.transactionId) {
                // Combine utilities and probabilities
                double newUtility = e1.utility + e2.utility;
                double newRemaining = Math.min(e1.remainingUtility, e2.remainingUtility);
                double newLogProbability = e1.logProbability + e2.logProbability;

                // Only add if probability is significant
                if (newLogProbability > LOG_EPSILON) {
                    joinedElements.add(new Element(
                        e1.transactionId, 
                        newUtility, 
                        newRemaining, 
                        newLogProbability
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

        // Return null if no common transactions
        if (joinedElements.isEmpty()) {
            return null;
        }

        return new UtilityList(newItemset, joinedElements);
    }

    /**
     * Creates a utility list for a single item from the database.
     * 
     * @param itemId The item ID
     * @param database List of transactions
     * @param itemProfits Map of item profits
     * @param itemRanking Map for calculating remaining utility
     * @return Utility list for the single item
     */
    public static UtilityList createSingleItemUtilityList(int itemId, 
                                                          List<Transaction> database,
                                                          Map<Integer, Double> itemProfits,
                                                          Map<Integer, Integer> itemRanking) {
        Set<Integer> itemset = Collections.singleton(itemId);
        List<Element> elements = new ArrayList<>();
        
        Double itemProfit = itemProfits.get(itemId);
        if (itemProfit == null) {
            return new UtilityList(itemset);
        }

        for (Transaction transaction : database) {
            if (transaction.containsItem(itemId)) {
                double probability = transaction.getProbability(itemId);
                
                // Skip items with negligible probability
                if (probability <= EPSILON) {
                    continue;
                }

                int quantity = transaction.getQuantity(itemId);
                double utility = itemProfit * quantity;
                double logProbability = Math.log(probability);
                
                // Calculate remaining utility (items with higher rank)
                double remainingUtility = calculateRemainingUtility(
                    transaction, itemId, itemProfits, itemRanking
                );

                elements.add(new Element(
                    transaction.getTransactionId(),
                    utility,
                    remainingUtility,
                    logProbability
                ));
            }
        }

        return new UtilityList(itemset, elements);
    }

    /**
     * Calculates remaining utility for extensions (items with higher rank).
     * 
     * @param transaction Current transaction
     * @param currentItem Current item being processed
     * @param itemProfits Map of item profits
     * @param itemRanking Map of item rankings (RTWU order)
     * @return Remaining positive utility
     */
    private static double calculateRemainingUtility(Transaction transaction, 
                                                   int currentItem,
                                                   Map<Integer, Double> itemProfits,
                                                   Map<Integer, Integer> itemRanking) {
        double remaining = 0.0;
        Integer currentRank = itemRanking.get(currentItem);
        
        if (currentRank == null) {
            return 0.0;
        }

        for (Integer itemId : transaction.getItemIds()) {
            Integer itemRank = itemRanking.get(itemId);
            
            // Only consider items with higher rank (RTWU order)
            if (itemRank != null && itemRank > currentRank) {
                Double profit = itemProfits.get(itemId);
                if (profit != null && profit > 0) {
                    remaining += profit * transaction.getQuantity(itemId);
                }
            }
        }

        return remaining;
    }

    /**
     * Filters elements based on minimum probability threshold.
     * 
     * @param minProbability Minimum probability threshold
     * @return New utility list with filtered elements
     */
    public UtilityList filterByProbability(double minProbability) {
        List<Element> filteredElements = new ArrayList<>();
        
        for (Element element : elements) {
            if (element.getProbability() >= minProbability) {
                filteredElements.add(element);
            }
        }
        
        return new UtilityList(itemset, filteredElements);
    }

    /**
     * Gets the upper bound for extensions (sum of EU and remaining).
     * 
     * @return Upper bound value
     */
    public double getUpperBound() {
        return sumExpectedUtility + sumRemainingUtility;
    }

    /**
     * Checks if this utility list can be pruned based on thresholds.
     * 
     * @param minExpectedUtility Minimum expected utility threshold
     * @param minProbability Minimum probability threshold
     * @return True if can be pruned, false otherwise
     */
    public boolean canBePruned(double minExpectedUtility, double minProbability) {
        return getUpperBound() < minExpectedUtility || 
               existentialProbability < minProbability;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        UtilityList that = (UtilityList) obj;
        return Objects.equals(itemset, that.itemset) &&
               Objects.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemset, elements);
    }

    @Override
    public String toString() {
        return String.format("UtilityList{itemset=%s, elements=%d, EU=%.2f, EP=%.4f}", 
                           itemset, elements.size(), sumExpectedUtility, existentialProbability);
    }

    /**
     * Detailed string representation for debugging.
     * 
     * @return Detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UtilityList{\n");
        sb.append("  itemset: ").append(itemset).append("\n");
        sb.append("  sumEU: ").append(String.format("%.4f", sumExpectedUtility)).append("\n");
        sb.append("  sumRemaining: ").append(String.format("%.4f", sumRemainingUtility)).append("\n");
        sb.append("  existentialProb: ").append(String.format("%.4f", existentialProbability)).append("\n");
        sb.append("  elements: [\n");
        
        for (Element element : elements) {
            sb.append("    ").append(element.toString()).append("\n");
        }
        
        sb.append("  ]\n}");
        return sb.toString();
    }

    /**
     * Builder class for creating utility lists incrementally.
     */
    public static class Builder {
        private final Set<Integer> itemset;
        private final List<Element> elements;

        public Builder(Set<Integer> itemset) {
            this.itemset = new HashSet<>(itemset);
            this.elements = new ArrayList<>();
        }

        public Builder addElement(int tid, double utility, double remaining, double logProb) {
            elements.add(new Element(tid, utility, remaining, logProb));
            return this;
        }

        public Builder addElement(Element element) {
            elements.add(element);
            return this;
        }

        public UtilityList build() {
            return new UtilityList(itemset, elements);
        }
    }
}