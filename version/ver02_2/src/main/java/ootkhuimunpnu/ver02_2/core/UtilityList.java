package ootkhuimunpnu.ver02_2.core;

import ootkhuimunpnu.ver02_2.engine.*;
import java.util.*;

/**
 * Enhanced Utility-List with log-space probability tracking
 * 
 * MODIFICATIONS:
 * - Store log-space probabilities to prevent numerical underflow
 * 
 * @author Meg
 * @version 2.2
 */
public class UtilityList {
    
    public static class Element {
        private final int tid;
        private final double utility;         // Actual utility in this transaction
        private final double remaining;       // Remaining positive utility
        private final double logProbability;  // Log probability for numerical stability

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
    }

    private final Set<Integer> itemset;
    private final List<Element> elements;
    private final double sumEU;                    // Sum of expected utilities
    private final double sumRemaining;             // Upper bound for extensions
    private final double existentialProbability;   // Correctly calculated EP

    public UtilityList(Set<Integer> itemset) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.elements = new ArrayList<>();
        this.sumEU = 0;
        this.sumRemaining = 0;
        this.existentialProbability = 0;
    }

    public UtilityList(Set<Integer> itemset, List<Element> elements) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.elements = Collections.unmodifiableList(elements);

        // Calculate aggregates
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
     * EP(X) = 1 - ∏(T∈D, X⊆T) [1 - P(X,T)]
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

        // Convert back: EP = 1 - exp(logComplement)
        if (logComplement < Configuration.LOG_EPSILON) {
            return 1.0;
        }

        double complement = Math.exp(logComplement);
        return 1.0 - complement;
    }

    /**
     * Join two utility-lists with log-space probability handling
     */
    public static UtilityList join(UtilityList ul1, UtilityList ul2) {
        Set<Integer> newItemset = new HashSet<>(ul1.itemset);
        newItemset.addAll(ul2.itemset);

        List<Element> joinedElements = new ArrayList<>();

        int i = 0, j = 0;
        while (i < ul1.elements.size() && j < ul2.elements.size()) {
            Element e1 = ul1.elements.get(i);
            Element e2 = ul2.elements.get(j);

            if (e1.tid == e2.tid) {
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

        return new UtilityList(newItemset, joinedElements);
    }

    // Getters
    public Set<Integer> getItemset() {
        return itemset;
    }

    public List<Element> getElements() {
        return elements;
    }

    public double getSumEU() {
        return sumEU;
    }

    public double getSumRemaining() {
        return sumRemaining;
    }

    public double getExistentialProbability() {
        return existentialProbability;
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public int size() {
        return elements.size();
    }
}