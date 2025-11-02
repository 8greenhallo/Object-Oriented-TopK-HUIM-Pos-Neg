package ootkhuimunpnu.ver03.core;

import ootkhuimunpnu.ver03.engine.*;
import java.util.*;

/**
 * Enhanced Utility-List with log-space probability tracking
 * 
 * Design Principles:
 * - Encapsulation: Internal Element class and controlled access
 * - Immutability: All data is final after construction
 * - Lazy Evaluation: Existential probability calculated once on construction
 * - Numerical Stability: Uses log-space probability calculations
 * 
 * Key Metrics:
 * - sumEU: Expected utility of the itemset
 * - sumRemaining: Upper bound for potential extensions
 * - existentialProbability: Probability that itemset appears in database
 * - rtwu: Redefined Transaction-Weighted Utility for pruning
 * 
 * MODIFICATIONS:
 * - Store log-space probabilities to prevent numerical underflow
 * 
 * @author Meg
 * @version 3.0
 */
public class UtilityList {
    /**
     * Represents a single element in the utility list.
     * Each element corresponds to one transaction containing the itemset.
     */
    public static class Element {
        private final int tid;
        private final double utility;         // Actual utility in this transaction
        private final double remaining;       // Remaining positive utility
        private final double logProbability;  // Log probability for numerical stability
        
        /**
         * Constructs a utility list element.
         * 
         * @param tid Transaction ID
         * @param utility Actual utility value
         * @param remaining Remaining utility for extensions
         * @param logProbability Log of probability value
         */
        public Element(int tid, double utility, double remaining, double logProbability) {
            this.tid = tid;
            this.utility = utility;
            this.remaining = remaining;
            this.logProbability = logProbability;
        }

        public int getTid() {
            return tid;
        }

        public double getUtility() {
            return utility;
        }

        public double getRemaining() {
            return remaining;
        }

        public double getLogProbability() {
            return logProbability;
        }

        public double getProbability() {
            return Math.exp(logProbability);
        }

        @Override
        public String toString() {
            return String.format("Element[tid=%d, u=%.2f, rem=%.2f, prob=%.4f]",
                               tid, utility, remaining, getProbability());
        }
    }

    // Core utility list data
    private final Set<Integer> itemset;
    private final List<Element> elements;
    private final double rtwu;
    
    // Computed metrics (calculated once at construction)
    private final double sumEU;                    // Sum of expected utilities
    private final double sumRemaining;             // Upper bound for extensions
    private final double existentialProbability;   // Correctly calculated EP

    /**
     * Constructs a utility list from itemset and elements.
     * All metrics are computed at construction time.
     * 
     * @param itemset Set of items in this utility list
     * @param elements List of elements (one per transaction)
     * @param rtwu Redefined Transaction-Weighted Utility
     */

    public UtilityList(Set<Integer> itemset) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.elements = new ArrayList<>();
        this.sumEU = 0;
        this.sumRemaining = 0;
        this.existentialProbability = 0;
        this.rtwu = 0;
    }

    public UtilityList(Set<Integer> itemset, List<Element> elements, double rtwu) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.elements = Collections.unmodifiableList(elements);
        this.rtwu = rtwu;

        // Calculate aggregates (expected utility and remaining utility)
        double eu = 0, rem = 0;

        for (Element e : elements) {
            double prob = Math.exp(e.logProbability);
            eu += e.utility * prob;    // Expected utility
            rem += e.remaining * prob; // Expected remaining
        }

        this.sumEU = eu;
        this.sumRemaining = rem;

        // Calculate existential probability in log-space
        this.existentialProbability = calculateLogSpaceExistentialProbability();
    }

    /**
     * Calculate existential probability using log-space for numerical stability
     * 
     * Formula: EP(X) = 1 - ∏(T∈D, X⊆T) [1 - P(X,T)]
     * 
     * Uses log-space to prevent numerical underflow:
     * log(EP) = log(1 - exp(∑ log(1 - P(X,T))))
     * 
     * @return Existential probability in [0, 1]
     */
    private double calculateLogSpaceExistentialProbability() {
        if (elements.isEmpty()) return 0.0;

        // Sum of log(1 - P(X,T)) for all transactions
        double logComplement = 0.0;

        for (Element e : elements) {
            if (e.logProbability > Math.log(1.0 - Configuration.EPSILON)) {
                // Probability is essentially 1
                return 1.0;
            }

            // Compute log(1 - P) stably using log1p
            double prob = Math.exp(e.logProbability);
            double log1MinusP = prob < 0.5 ?
                Math.log1p(-prob) :
                Math.log(1.0 - prob);

            logComplement += log1MinusP;

            // Early termination if product becomes too small
            if (logComplement < Configuration.LOG_EPSILON) {
                return 1.0;
            }
        }

        // Convert back from log-space: EP = 1 - exp(logComplement)
        if (logComplement < Configuration.LOG_EPSILON) {
            return 1.0;
        }

        double complement = Math.exp(logComplement);
        return 1.0 - complement;
    }

    /**
     * Joins this utility list with another to create a new utility list.
     * Uses transaction intersection and probability multiplication in log-space.
     * 
     * @param other Another utility list to join with
     * @return New utility list representing the union of itemsets, or null if join fails
     */
    public static UtilityList join(UtilityList ul1, UtilityList ul2) {
        // Create union of itemsets
        Set<Integer> newItemset = new HashSet<>(ul1.itemset);
        newItemset.addAll(ul2.itemset);

        // Calculate joined RTWU (minimum of the two)
        double joinedRTWU = Math.min(ul1.rtwu, ul2.rtwu);
        
        // Join elements on matching transaction IDs
        List<Element> joinedElements = new ArrayList<>();

        int i = 0, j = 0;
        while (i < ul1.elements.size() && j < ul2.elements.size()) {
            Element e1 = ul1.elements.get(i);
            Element e2 = ul2.elements.get(j);

            if (e1.tid == e2.tid) {
                // Combine utilities
                double newUtility = e1.utility + e2.utility;
                double newRemaining = Math.min(e1.remaining, e2.remaining);

                // Multiply probabilities in log-space
                double newLogProbability = e1.logProbability + e2.logProbability;

                // Only add if probability is significant
                if (newLogProbability > Configuration.LOG_EPSILON) {
                    joinedElements.add(new Element(
                        e1.tid, newUtility, newRemaining, newLogProbability
                    ));
                }
                i++;
                j++;
            } else if (e1.tid < e2.tid) {
                i++;
            } else {
                j++;
            }
        }

        if (joinedElements.isEmpty()) {
            return null;
        }

        return new UtilityList(newItemset, joinedElements, joinedRTWU);
    }

    // ==================== Accessor Methods ====================
    
    /**
     * Gets the itemset represented by this utility list.
     */
    public Set<Integer> getItemset() {
        return itemset;
    }

    /**
     * Gets all elements in this utility list.
     */
    public List<Element> getElements() {
        return elements;
    }

    /**
     * Gets the sum of expected utilities.
     * This is the actual expected utility of the itemset.
     */
    public double getSumEU() {
        return sumEU;
    }

    /**
     * Gets the sum of expected remaining utilities.
     * This provides an upper bound for extensions.
     */
    public double getSumRemaining() {
        return sumRemaining;
    }

    /**
     * Gets the existential probability.
     * Probability that this itemset appears at least once in the database.
     */
    public double getExistentialProbability() {
        return existentialProbability;
    }

    /**
     * Gets the RTWU value.
     * Used for pruning and ordering.
     */
    public double getRtwu() {
        return rtwu;
    }

    /**
     * Checks if this utility list is empty.
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * Gets the number of elements (transactions) in this utility list.
     */
    public int size() {
        return elements.size();
    }

    /**
     * Gets the size of the itemset.
     */
    public int getItemsetSize() {
        return itemset.size();
    }

    @Override
    public String toString() {
        return String.format("UtilityList[itemset=%s, elements=%d, EU=%.2f, EP=%.4f, RTWU=%.2f]",
                           itemset, elements.size(), sumEU, existentialProbability, rtwu);
    }
}