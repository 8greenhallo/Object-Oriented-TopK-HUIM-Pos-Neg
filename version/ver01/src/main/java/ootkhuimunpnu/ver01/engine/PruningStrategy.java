package ootkhuimunpnu.ver01.engine;

import ootkhuimunpnu.ver01.core.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Advanced pruning strategies for OOTK-HUIM-UN-PNU algorithm.
 * Implements multiple pruning techniques to reduce search space and
 * improve mining efficiency while maintaining correctness.
 * 
 * @author Meg
 * @version 1.0
 */
public class PruningStrategy {
    
    private final Configuration config;
    
    // Performance counters
    private int rtwuPruned = 0;
    private int existentialProbabilityPruned = 0;
    private int upperBoundPruned = 0;
    private int bulkPruned = 0;
    private int totalPruned = 0;
    private int totalEvaluated = 0;

    /**
     * Creates a new pruning strategy with the given configuration.
     * 
     * @param config Mining algorithm configuration
     */
    public PruningStrategy(Configuration config) {
        this.config = config;
    }
    
    /**
     * Evaluates whether an itemset can be pruned based on RTWU.
     * 
     * @param itemRTWU RTWU value of the item/itemset
     * @param threshold Current mining threshold
     * @return True if can be pruned, false otherwise
     */
    public boolean canPruneByRTWU(double itemRTWU, double threshold) {
        totalEvaluated++;
        
        if (!config.isRTWUPruningEnabled()) {
            return false;
        }
        
        if (itemRTWU < threshold - config.getEpsilon()) {
            rtwuPruned++;
            totalPruned++;
            return true;
        }
        
        return false;
    }
    
    /**
     * Evaluates whether an itemset can be pruned based on existential probability.
     * 
     * @param existentialProbability Existential probability of the itemset
     * @param minProbability Minimum probability threshold
     * @return True if can be pruned, false otherwise
     */
    public boolean canPruneByExistentialProbability(double existentialProbability, double minProbability) {
        totalEvaluated++;
        
        if (!config.isExistentialProbabilityPruningEnabled()) {
            return false;
        }
        
        if (existentialProbability < minProbability - config.getEpsilon()) {
            existentialProbabilityPruned++;
            totalPruned++;
            return true;
        }
        
        return false;
    }
    
    /**
     * Evaluates whether an itemset can be pruned based on utility upper bound.
     * Uses the sum of expected utility and remaining utility as upper bound.
     * 
     * @param utilityList Utility list to evaluate
     * @param threshold Current mining threshold
     * @return True if can be pruned, false otherwise
     */
    public boolean canPruneByUpperBound(UtilityList utilityList, double threshold) {
        totalEvaluated++;
        
        if (!config.isUtilityUpperBoundPruningEnabled()) {
            return false;
        }
        
        double upperBound = utilityList.getSumExpectedUtility() + utilityList.getSumRemainingUtility();
        
        if (upperBound < threshold - config.getEpsilon()) {
            upperBoundPruned++;
            totalPruned++;
            return true;
        }
        
        return false;
    }
    
    /**
     * Evaluates whether an itemset can be pruned using combined criteria.
     * This is the main pruning method that combines multiple strategies.
     * 
     * @param utilityList Utility list to evaluate
     * @param threshold Current mining threshold
     * @param minProbability Minimum probability threshold
     * @return True if can be pruned, false otherwise
     */
    public boolean canPrune(UtilityList utilityList, double threshold, double minProbability) {
        if (utilityList == null || utilityList.isEmpty()) {
            return true;
        }
        
        // Strategy 1: Existential probability pruning
        if (canPruneByExistentialProbability(utilityList.getExistentialProbability(), minProbability)) {
            return true;
        }
        
        // Strategy 2: Upper bound pruning (EU + remaining)
        if (canPruneByUpperBound(utilityList, threshold)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Bulk pruning for multiple utility lists based on minimum RTWU.
     * Efficiently prunes groups of candidates that cannot meet the threshold.
     * 
     * @param utilityLists List of utility lists to evaluate
     * @param threshold Current mining threshold
     * @return List of utility lists that passed pruning
     */
    public List<UtilityList> bulkPruneByRTWU(List<UtilityList> utilityLists, double threshold) {
        if (!config.isBulkPruningEnabled() || utilityLists.isEmpty()) {
            return new ArrayList<>(utilityLists);
        }
        
        List<UtilityList> survivors = new ArrayList<>();
        int prunedCount = 0;
        
        // Find minimum RTWU for early termination
        double minRTWU = utilityLists.stream()
            .mapToDouble(ul -> getRTWUFromUtilityList(ul))
            .min()
            .orElse(Double.MAX_VALUE);
        
        // If minimum RTWU is below threshold, prune entire batch
        if (minRTWU < threshold - config.getEpsilon()) {
            bulkPruned++;
            totalPruned+= utilityLists.size();
            return survivors; // Empty list
        }
        
        // Individual evaluation for remaining items
        for (UtilityList ul : utilityLists) {
            double rtwu = getRTWUFromUtilityList(ul);
            if (rtwu >= threshold - config.getEpsilon()) {
                survivors.add(ul);
            } else {
                prunedCount++;
            }
        }
        
        if (prunedCount > 0) {
            rtwuPruned += prunedCount;
            totalPruned += prunedCount;
        }
        
        return survivors;
    }
    
    /**
     * Advanced pruning that considers the relationship between utility and probability.
     * Uses a combined score to make more informed pruning decisions.
     * 
     * @param utilityList Utility list to evaluate
     * @param threshold Current mining threshold
     * @param minProbability Minimum probability threshold
     * @param alpha Weight factor for combining utility and probability (0 <= alpha <= 1)
     * @return True if can be pruned, false otherwise
     */
    public boolean canPruneByCombiledScore(UtilityList utilityList, double threshold, 
                                         double minProbability, double alpha) {
        if (utilityList == null || utilityList.isEmpty()) {
            return true;
        }
        
        double expectedUtility = utilityList.getSumExpectedUtility();
        double existentialProb = utilityList.getExistentialProbability();
        
        // Combined score: alpha * normalized_utility + (1-alpha) * probability
        double normalizedUtility = Math.min(1.0, expectedUtility / Math.max(threshold, 1.0));
        double combinedScore = alpha * normalizedUtility + (1.0 - alpha) * existentialProb;
        double minCombinedScore = alpha * 1.0 + (1.0 - alpha) * minProbability;
        
        totalEvaluated++;
        
        if (combinedScore < minCombinedScore - config.getEpsilon()) {
            totalPruned++;
            return true;
        }
        
        return false;
    }
    
    /**
     * Prunes based on itemset size and utility density.
     * Large itemsets with low utility density can be pruned early.
     * 
     * @param utilityList Utility list to evaluate
     * @param maxItemsetSize Maximum allowed itemset size
     * @param minUtilityDensity Minimum utility per item
     * @return True if can be pruned, false otherwise
     */
    public boolean canPruneBySize(UtilityList utilityList, int maxItemsetSize, double minUtilityDensity) {
        if (utilityList == null || utilityList.isEmpty()) {
            return true;
        }
        
        int itemsetSize = utilityList.getItemsetSize();
        
        // Prune if itemset is too large
        if (maxItemsetSize > 0 && itemsetSize > maxItemsetSize) {
            totalEvaluated++;
            totalPruned++;
            return true;
        }
        
        // Prune if utility density is too low
        if (minUtilityDensity > 0) {
            double utilityDensity = utilityList.getSumExpectedUtility() / itemsetSize;
            if (utilityDensity < minUtilityDensity - config.getEpsilon()) {
                totalEvaluated++;
                totalPruned++;
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Estimates the RTWU value from a utility list.
     * This is a helper method for RTWU-based pruning.
     * 
     * @param utilityList Utility list
     * @return Estimated RTWU value
     */
    private double getRTWUFromUtilityList(UtilityList utilityList) {
        // For simplicity, use upper bound as RTWU estimate
        // In practice, this should be the actual RTWU calculation
        return utilityList.getUpperBound();
    }
    
    /**
     * Adaptive threshold adjustment based on current mining progress.
     * Dynamically adjusts pruning aggressiveness based on performance.
     * 
     * @param currentThreshold Current mining threshold
     * @param candidatesEvaluated Number of candidates evaluated so far
     * @param targetCandidates Target number of candidates to evaluate
     * @return Adjusted threshold
     */
    public double adjustThreshold(double currentThreshold, long candidatesEvaluated, long targetCandidates) {
        if (candidatesEvaluated == 0 || targetCandidates == 0) {
            return currentThreshold;
        }
        
        double progress = (double) candidatesEvaluated / targetCandidates;
        double pruningRate = getPruningRate();
        
        // If pruning rate is low, increase threshold to be more aggressive
        if (pruningRate < 0.3 && progress > 0.5) {
            return currentThreshold * 1.1; // Increase threshold by 10%
        }
        
        // If pruning rate is very high, decrease threshold to be less aggressive
        if (pruningRate > 0.8 && progress < 0.8) {
            return currentThreshold * 0.95; // Decrease threshold by 5%
        }
        
        return currentThreshold;
    }
    
    /**
     * Evaluates the effectiveness of current pruning strategies.
     * 
     * @return Pruning effectiveness metrics
     */
    public PruningEffectiveness evaluateEffectiveness() {
        long totalEval = totalEvaluated;
        long totalPrun = totalPruned;
        
        return new PruningEffectiveness(
            totalEval,
            totalPrun,
            rtwuPruned,
            existentialProbabilityPruned,
            upperBoundPruned,
            bulkPruned
        );
    }
    
    /**
     * Gets the current pruning rate (percentage of candidates pruned).
     * 
     * @return Pruning rate as a value between 0.0 and 1.0
     */
    public double getPruningRate() {
        long total = totalEvaluated;
        return total > 0 ? (double) totalPruned / total : 0.0;
    }
    
    /**
     * Resets all performance counters.
     */
    public void resetCounters() {
        rtwuPruned = 0;
        existentialProbabilityPruned = 0;
        upperBoundPruned = 0;
        bulkPruned = 0;
        totalPruned = 0;
        totalEvaluated = 0;
    }
    
    /**
     * Gets detailed pruning statistics.
     * 
     * @return Map containing pruning statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long total = totalEvaluated;
        long pruned = totalPruned;
        
        stats.put("totalEvaluated", total);
        stats.put("totalPruned", pruned);
        stats.put("pruningRate", total > 0 ? (double) pruned / total : 0.0);
        
        stats.put("rtwuPruned", rtwuPruned);
        stats.put("existentialProbabilityPruned", existentialProbabilityPruned);
        stats.put("upperBoundPruned", upperBoundPruned);
        stats.put("bulkPruned", bulkPruned);
        
        if (total > 0) {
            stats.put("rtwuPruningRate", (double) rtwuPruned / total);
            stats.put("existentialProbabilityPruningRate", (double) existentialProbabilityPruned / total);
            stats.put("upperBoundPruningRate", (double) upperBoundPruned / total);
            stats.put("bulkPruningRate", (double) bulkPruned / total);
        }
        
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("PruningStrategy{pruned=%d/%d (%.2f%%)}", 
                           totalPruned, totalEvaluated, getPruningRate() * 100);
    }
    
    /**
     * Pruning effectiveness metrics class.
     */
    public static class PruningEffectiveness {
        private final long totalEvaluated;
        private final long totalPruned;
        private final long rtwuPruned;
        private final long existentialProbabilityPruned;
        private final long upperBoundPruned;
        private final long bulkPruned;
        
        public PruningEffectiveness(long totalEvaluated, long totalPruned, 
                                   long rtwuPruned, long existentialProbabilityPruned,
                                   long upperBoundPruned, long bulkPruned) {
            this.totalEvaluated = totalEvaluated;
            this.totalPruned = totalPruned;
            this.rtwuPruned = rtwuPruned;
            this.existentialProbabilityPruned = existentialProbabilityPruned;
            this.upperBoundPruned = upperBoundPruned;
            this.bulkPruned = bulkPruned;
        }
        
        public long getTotalEvaluated() { return totalEvaluated; }
        public long getTotalPruned() { return totalPruned; }
        public long getRtwuPruned() { return rtwuPruned; }
        public long getExistentialProbabilityPruned() { return existentialProbabilityPruned; }
        public long getUpperBoundPruned() { return upperBoundPruned; }
        public long getBulkPruned() { return bulkPruned; }
        
        public double getPruningRate() {
            return totalEvaluated > 0 ? (double) totalPruned / totalEvaluated : 0.0;
        }
        
        public double getRtwuPruningRate() {
            return totalEvaluated > 0 ? (double) rtwuPruned / totalEvaluated : 0.0;
        }
        
        public double getExistentialProbabilityPruningRate() {
            return totalEvaluated > 0 ? (double) existentialProbabilityPruned / totalEvaluated : 0.0;
        }
        
        public double getUpperBoundPruningRate() {
            return totalEvaluated > 0 ? (double) upperBoundPruned / totalEvaluated : 0.0;
        }
        
        public double getBulkPruningRate() {
            return totalEvaluated > 0 ? (double) bulkPruned / totalEvaluated : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PruningEffectiveness{evaluated=%d, pruned=%d (%.2f%%), " +
                "RTWU=%d (%.2f%%), EP=%d (%.2f%%), UB=%d (%.2f%%), Bulk=%d (%.2f%%)}",
                totalEvaluated, totalPruned, getPruningRate() * 100,
                rtwuPruned, getRtwuPruningRate() * 100,
                existentialProbabilityPruned, getExistentialProbabilityPruningRate() * 100,
                upperBoundPruned, getUpperBoundPruningRate() * 100,
                bulkPruned, getBulkPruningRate() * 100
            );
        }
    }
    
    /**
     * Factory class for creating specialized pruning strategies.
     */
    public static class Factory {
        
        /**
         * Creates a conservative pruning strategy (less aggressive).
         * 
         * @param config Base configuration
         * @return Conservative pruning strategy
         */
        public static PruningStrategy createConservative(Configuration config) {
            Configuration conservativeConfig = config.copy();
            conservativeConfig.setRTWUPruningEnabled(true);
            conservativeConfig.setExistentialProbabilityPruningEnabled(true);
            conservativeConfig.setUtilityUpperBoundPruningEnabled(false); // Disabled for conservative approach
            conservativeConfig.setBulkPruningEnabled(false);
            
            return new PruningStrategy(conservativeConfig);
        }
        
        /**
         * Creates an aggressive pruning strategy (more pruning).
         * 
         * @param config Base configuration
         * @return Aggressive pruning strategy
         */
        public static PruningStrategy createAggressive(Configuration config) {
            Configuration aggressiveConfig = config.copy();
            aggressiveConfig.setRTWUPruningEnabled(true);
            aggressiveConfig.setExistentialProbabilityPruningEnabled(true);
            aggressiveConfig.setUtilityUpperBoundPruningEnabled(true);
            aggressiveConfig.setBulkPruningEnabled(true);
            
            return new PruningStrategy(aggressiveConfig);
        }
        
        /**
         * Creates a balanced pruning strategy (default).
         * 
         * @param config Base configuration
         * @return Balanced pruning strategy
         */
        public static PruningStrategy createBalanced(Configuration config) {
            return new PruningStrategy(config); // Use configuration as-is
        }
        
        /**
         * Creates a custom pruning strategy with specific settings.
         * 
         * @param config Base configuration
         * @param enableRTWU Enable RTWU pruning
         * @param enableEP Enable existential probability pruning
         * @param enableUB Enable upper bound pruning
         * @param enableBulk Enable bulk pruning
         * @return Custom pruning strategy
         */
        public static PruningStrategy createCustom(Configuration config, 
                                                 boolean enableRTWU, boolean enableEP,
                                                 boolean enableUB, boolean enableBulk) {
            Configuration customConfig = config.copy();
            customConfig.setRTWUPruningEnabled(enableRTWU);
            customConfig.setExistentialProbabilityPruningEnabled(enableEP);
            customConfig.setUtilityUpperBoundPruningEnabled(enableUB);
            customConfig.setBulkPruningEnabled(enableBulk);
            
            return new PruningStrategy(customConfig);
        }
    }
}