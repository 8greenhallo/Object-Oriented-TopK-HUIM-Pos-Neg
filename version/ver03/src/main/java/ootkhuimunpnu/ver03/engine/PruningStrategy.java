package ootkhuimunpnu.ver03.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ootkhuimunpnu.ver03.core.UtilityList;

/**
 * Pruning strategies for the mining algorithm.
 * Provides static methods for different pruning techniques to reduce search space.
 * 
 * Design Principles:
 * - Strategy Pattern: Different pruning methods as strategies
 * - Static Methods: Stateless utility functions
 * - Clear Documentation: Each strategy explains its mathematical basis
 * 
 * Pruning Strategies:
 * 1. RTWU Pruning: Based on Redefined Transaction-Weighted Utility
 * 2. EU+Remaining Pruning: Based on expected utility upper bound
 * 3. Existential Probability Pruning: Based on occurrence probability
 * 4. EUCS Pruning: Based on Estimated Utility Co-occurrence Structure
 * 
 * MODIFICATIONS:
 * 
 * @author Meg
 * @version 3.0
 */
public class PruningStrategy {
    private final Configuration config;

    // Prevent instantiation
    public PruningStrategy(Configuration config) {
        this.config = config;
    }

    /**
     * Item pair class structure for EUCS structure.
     */
    public static class ItemPair {
        private final int item1;
        private final int item2;
        
        public ItemPair(int a, int b) {
            // Ensure consistent ordering
            if (a < b) {
                this.item1 = a;
                this.item2 = b;
            } else {
                this.item1 = b;
                this.item2 = a;
            }
        }
        
        public int getItem1() {
            return item1;
        }
        
        public int getItem2() {
            return item2;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemPair)) return false;
            ItemPair pair = (ItemPair) o;
            return item1 == pair.item1 && item2 == pair.item2;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(item1, item2);
        }
        
        @Override
        public String toString() {
            return String.format("(%d,%d)", item1, item2);
        }
    }

    // ==================== Static pruning strategies ====================

    /**
     * Strategy 1: RTWU-based pruning.
     * 
     * Theorem: If RTWU(X) < threshold, then X and all its supersets cannot be
     * high-utility itemsets.
     * 
     * @param rtwu RTWU value of the itemset
     * @param threshold Current minimum utility threshold
     * @return true if itemset should be pruned
     */
    public static boolean shouldPruneByRTWU(double rtwu, double threshold) {
        return rtwu < threshold - Configuration.EPSILON;
    }

    /**
     * Strategy 2: EU + Remaining utility pruning.
     * 
     * Theorem: If EU(X) + remaining(X) < threshold, then X cannot be extended
     * to form a high-utility itemset.
     * 
     * @param utilityList Utility list to evaluate
     * @param threshold Current minimum utility threshold
     * @return true if itemset should be pruned
     */
    public static boolean shouldPruneByExpectedUtility(UtilityList utilityList, 
                                                       double threshold) {
        double upperBound = utilityList.getSumEU() + utilityList.getSumRemaining();
        return upperBound < threshold - Configuration.EPSILON;
    }

    /**
     * Strategy 3: Existential probability pruning.
     * 
     * Theorem: If EP(X) < minProbability, then X does not meet the minimum
     * probability constraint and should be pruned.
     * 
     * @param utilityList Utility list to evaluate
     * @param minProbability Minimum probability threshold
     * @return true if itemset should be pruned
     */
    public static boolean shouldPruneByProbability(UtilityList utilityList,
                                                   double minProbability) {
        return utilityList.getExistentialProbability() < minProbability - Configuration.EPSILON;
    }

    /**
     * Strategy 4: EUCS (Estimated Utility Co-occurrence Structure) pruning.
     * 
     * Theorem: If EUCS(i, j) < threshold for any pair of items in the itemset,
     * then the itemset cannot be high-utility.
     * 
     * @param itemset Set of items to check
     * @param eucs EUCS structure mapping item pairs to utilities
     * @param threshold Current minimum utility threshold
     * @return true if itemset should be pruned
     */
    public static boolean shouldPruneByEUCS(Set<Integer> itemset,
                                            Set<Integer> extension,
                                            Map<ItemPair, Double> eucs,
                                            double threshold) {
        
        Set<Integer> combined = new HashSet<>(itemset);
        combined.addAll(extension);

        List<Integer> items = new ArrayList<>(combined);
        
        // Check all pairs
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                ItemPair pair = new ItemPair(items.get(i), items.get(j));
                Double rtwu = eucs.get(pair);
                
                // If pair not in EUCS or has insufficient utility, prune
                if (rtwu == null || rtwu < threshold - Configuration.EPSILON) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Check if a utility list qualifies as a high-utility itemset
     * @param ul the utility list to check
     * @param currentThreshold the current top-k threshold
     * @return true if the utility list qualifies
     */
    public boolean isHighUtility(UtilityList ul, double currentThreshold) {
        if (ul == null || ul.isEmpty()) {
            return false;
        }

        return ul.getSumEU() >= currentThreshold - config.getEpsilon() &&
               ul.getExistentialProbability() >= config.getMinProbability() - config.getEpsilon();
    }

    /**
     * Check if a utility list passes the existential probability threshold
     */
    public boolean passesExistentialProbability(UtilityList ul) {
        if (ul == null || ul.isEmpty()) {
            return false;
        }
        
        return ul.getExistentialProbability() >= config.getMinProbability() - config.getEpsilon();
    }

    /**
     * Check if a utility list passes the utility threshold
     */
    public boolean passesUtilityThreshold(UtilityList ul, double threshold) {
        if (ul == null || ul.isEmpty()) {
            return false;
        }
        
        return ul.getSumEU() >= threshold - config.getEpsilon();
    }

    /**
     * Check if the upper bound allows for potential high-utility extensions
     */
    public boolean hasExtensionPotential(UtilityList ul, double threshold) {
        if (ul == null || ul.isEmpty()) {
            return false;
        }
        
        return ul.getSumEU() + ul.getSumRemaining() >= threshold - config.getEpsilon();
    }

    /**
     * Early EUCS check for single item extension.
     * Checks if a single item can be added to a prefix without violating EUCS.
     * 
     * @param prefix Existing itemset
     * @param newItem Item to potentially add
     * @param eucs EUCS structure
     * @param threshold Current minimum utility threshold
     * @return true if extension should be pruned
     */
    public static boolean shouldPruneEarlyEUCS(Set<Integer> prefix,
                                              int newItem,
                                              Map<ItemPair, Double> eucs,
                                              double threshold) {
        for (Integer prefixItem : prefix) {
            ItemPair pair = new ItemPair(prefixItem, newItem);
            Double rtwu = eucs.get(pair);
            
            if (rtwu == null || rtwu < threshold - Configuration.EPSILON) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Combined pruning check.
     * Applies multiple pruning strategies in order of computational cost.
     * 
     * @param ul Utility list to evaluate
     * @param threshold Current minimum utility threshold
     * @param minProbability Minimum probability threshold
     * @param eucs EUCS structure (can be null to skip EUCS pruning)
     * @return Pruning result with reason
     */
    public PruningResult shouldPrune(UtilityList ul, 
                                UtilityList extension,
                                double currentThreshold,
                                double minProbability,
                                Map<ItemPair, Double> eucs) {
        // Check 1: RTWU pruning (fastest)
        if (shouldPruneByRTWU(ul.getRtwu(), currentThreshold)) {
            return new PruningResult(true, "RTWU");
        }
        
        // Check 2: Existential probability pruning (fast)
        if (shouldPruneByProbability(ul, minProbability)) {
            return new PruningResult(true, "Probability");
        }
        
        // Check 3: EU + Remaining pruning (moderate)
        if (shouldPruneByExpectedUtility(ul, currentThreshold)) {
            return new PruningResult(true, "ExpectedUtility");
        }
        
        // Check 4: EUCS pruning (slower, only if EUCS provided)
        if (eucs != null && shouldPruneByEUCS(ul.getItemset(), extension.getItemset(), eucs, currentThreshold)) {
            return new PruningResult(true, "EUCS");
        }
        
        // No pruning applied
        return new PruningResult(false, null);
    }

    // ==================== Logging Methods for debugging ====================

    /**
     * Result of pruning evaluation.
     */
    public static class PruningResult {
        private final boolean shouldPrune;
        private final String reason;
        
        public PruningResult(boolean shouldPrune, String reason) {
            this.shouldPrune = shouldPrune;
            this.reason = reason;
        }
        
        public boolean shouldPrune() {
            return shouldPrune;
        }
        
        public String getReason() {
            return reason;
        }
        
        @Override
        public String toString() {
            return shouldPrune ? "Prune[" + reason + "]" : "Keep";
        }
    }
    
    /**
     * Statistics tracker for pruning effectiveness.
     */
    public static class PruningStatistics {
        private long rtwuPruned = 0;
        private long probabilityPruned = 0;
        private long expectedUtilityPruned = 0;
        private long eucsPruned = 0;
        private long earlyEucsPruned = 0;
        private long totalCandidates = 0;
        
        public void recordPruning(String reason) {
            totalCandidates++;
            switch (reason) {
                case "RTWU": rtwuPruned++; break;
                case "Probability": probabilityPruned++; break;
                case "ExpectedUtility": expectedUtilityPruned++; break;
                case "EUCS": eucsPruned++; break;
                case "EarlyEUCS": earlyEucsPruned++; break;
            }
        }
        
        public void recordCandidate() {
            totalCandidates++;
        }
        
        private long getTotalPruned() {
            return rtwuPruned + probabilityPruned + expectedUtilityPruned + 
                   eucsPruned + earlyEucsPruned;
        }
        
        private double getPruningRate() {
            return totalCandidates > 0 ? 
                   (double) getTotalPruned() / totalCandidates : 0.0;
        }
        
        // For detailed breakdown, programmatic use (metrics, exports, dashboards)
        public Map<String, Long> getBreakdown() {
            Map<String, Long> breakdown = new LinkedHashMap<>();
            breakdown.put("RTWU: ", rtwuPruned);
            breakdown.put("Existential Probability: ", probabilityPruned);
            breakdown.put("Expected Utility + Remaining Utility: ", expectedUtilityPruned);
            breakdown.put("EUCS", eucsPruned);
            breakdown.put("Early EUCS", earlyEucsPruned);
            breakdown.put("Total", getTotalPruned());
            breakdown.put("Candidates checked", totalCandidates);
            return breakdown;
        }

        // Console or text report with human-readable format
        public String getStatistics() {
        return String.format("\nPruning Statistics:\n"+
                             " - Total candidates checked: %d\n" +
                             " - Total candidates pruned: %d (%.2f%%)\n"+
                             "   + RTWU: %d (%.2f%%)\n"+
                             "   + Existential Probability: %d (%.2f%%)\n"+
                             "   + Expected Utility + Remaining Utility: %d (%.2f%%)\n"+
                             "   + EUCS: %d (%.2f%%)\n"+
                             "   + Early EUCS: %d (%.2f%%)\n"+
                             " - Pruning rate: %.2f%%\n",
                            totalCandidates,
                            getTotalPruned(), totalCandidates > 0 ? (getTotalPruned() * 100.0 / totalCandidates) : 0.0, 
                            rtwuPruned, totalCandidates > 0 ? (rtwuPruned * 100.0 / getTotalPruned()) : 0.0,
                            probabilityPruned, totalCandidates > 0 ? (probabilityPruned * 100.0 / getTotalPruned()) : 0.0,
                            expectedUtilityPruned, totalCandidates > 0 ? (expectedUtilityPruned * 100.0 / getTotalPruned()) : 0.0,
                            eucsPruned, totalCandidates > 0 ? (eucsPruned * 100.0 / getTotalPruned()) : 0.0,
                            earlyEucsPruned, totalCandidates > 0 ? (earlyEucsPruned * 100.0 / getTotalPruned()) : 0.0,
                            getPruningRate() * 100);
        }
        
        @Override
        public String toString() {
            return String.format("PruningStats[total=%d, pruned=%d, rate=%.2f%%]",
                               totalCandidates, getTotalPruned(), getPruningRate() * 100);
        }
    }

    /*  Statistics methods
    // Pruning statistics
    private long euPruned = 0;
    private long epPruned = 0;
    private long totalPruned = 0;
    private long totalChecked = 0;

    public long getEuPruned() {
        return euPruned;
    }

    public long getEpPruned() {
        return epPruned;
    }

    public long getTotalPruned() {
        return totalPruned;
    }

    public long getTotalChecked() {
        return totalChecked;
    }

    public void resetStatistics() {
        euPruned = 0;
        epPruned = 0;
        totalPruned = 0;
        totalChecked = 0;
    }

    public String getStatistics() {
        return String.format("Pruning Statistics:\n"+
                             " - Total checks: %d\n" +
                             " - Total pruned: %d (%.2f%%)\n"+
                             "   + EU+Remaining pruned: %d (%.2f%%)\n"+
                             "   + Existential Probability pruned: %d (%.2f%%)",
                           totalChecked,
                           totalPruned, totalChecked > 0 ? (totalPruned * 100.0 / totalChecked) : 0.0, 
                           euPruned, totalChecked > 0 ? (euPruned * 100.0 / totalPruned) : 0.0,
                           epPruned, totalChecked > 0 ? (epPruned * 100.0 / totalPruned) : 0.0);
    }*/
}