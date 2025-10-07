package engine;

import core.*;
import java.util.*;

/**
 * Encapsulates mathematically proven pruning strategies for PTK-HUIM-U±.
 * This class implements the core pruning techniques used to reduce the search space
 * while maintaining correctness and completeness of the mining algorithm.
 * 
 * @author Meg
 * @version 1.1
 */
public class PruningStrategy {
    private final Configuration config;
    private final TopKManager topKManager;
    
    // Statistics tracking
    private long totalCandidatesEvaluated = 0;
    private long candidatesPrunedByEU = 0;
    private long candidatesPrunedByEP = 0;
    private long candidatesPrunedByEarlyTermination = 0;
    
    /**
     * Constructs a new PruningStrategy with the given configuration and top-K manager.
     * 
     * @param config The algorithm configuration
     * @param topKManager The top-K manager for threshold queries
     */
    public PruningStrategy(Configuration config, TopKManager topKManager) {
        this.config = config;
        this.topKManager = topKManager;
    }
    
    /**
     * Evaluates whether a utility-list should be pruned based on all available strategies.
     * This is the main entry point for pruning decisions.
     * 
     * @param utilityList The utility-list to evaluate
     * @return PruningResult containing the decision and reason
     */
    public PruningResult evaluateForPruning(UtilityList utilityList) {
        totalCandidatesEvaluated++;
        
        // Strategy 1: Existential Probability Pruning
        PruningResult epResult = checkExistentialProbabilityPruning(utilityList);
        if (epResult.shouldPrune()) {
            candidatesPrunedByEP++;
            return epResult;
        }
        
        // Strategy 2: Expected Utility + Remaining Utility Pruning
        PruningResult euResult = checkExpectedUtilityPruning(utilityList);
        if (euResult.shouldPrune()) {
            candidatesPrunedByEU++;
            return euResult;
        }
        
        // Strategy 3: Early Termination (if enabled)
        if (config.isEarlyTerminationEnabled()) {
            PruningResult etResult = checkEarlyTermination(utilityList);
            if (etResult.shouldPrune()) {
                candidatesPrunedByEarlyTermination++;
                return etResult;
            }
        }
        
        // No pruning applicable
        return PruningResult.noPruning();
    }
    
    /**
     * Strategy 1: Existential Probability Pruning
     * Prunes itemsets whose existential probability is below the minimum threshold.
     * This is mathematically sound because such itemsets cannot be high-utility.
     * 
     * @param utilityList The utility-list to check
     * @return Pruning result
     */
    private PruningResult checkExistentialProbabilityPruning(UtilityList utilityList) {
        double existentialProbability = utilityList.getExistentialProbability();
        double threshold = config.getMinProbability();
        
        if (existentialProbability < threshold - config.getEpsilon()) {
            return PruningResult.pruned(PruningReason.EXISTENTIAL_PROBABILITY,
                String.format("EP=%.6f < threshold=%.6f", existentialProbability, threshold));
        }
        
        return PruningResult.noPruning();
    }
    
    /**
     * Strategy 2: Expected Utility + Remaining Utility Pruning
     * Prunes itemsets where EU + remaining utility < current k-th highest utility.
     * This is mathematically sound because no extension can exceed this upper bound.
     * 
     * @param utilityList The utility-list to check
     * @return Pruning result
     */
    private PruningResult checkExpectedUtilityPruning(UtilityList utilityList) {
        double expectedUtility = utilityList.getSumExpectedUtility();
        double remainingUtility = utilityList.getSumRemainingUtility();
        double currentThreshold = topKManager.getThreshold();
        
        double upperBound = expectedUtility + remainingUtility;
        
        if (upperBound < currentThreshold - config.getEpsilon()) {
            return PruningResult.pruned(PruningReason.EXPECTED_UTILITY,
                String.format("EU+RU=%.6f < threshold=%.6f", upperBound, currentThreshold));
        }
        
        return PruningResult.noPruning();
    }
    
    /**
     * Strategy 3: Early Termination
     * Additional heuristic pruning when the search space becomes too large.
     * This is not mathematically proven but can provide performance benefits.
     * 
     * @param utilityList The utility-list to check
     * @return Pruning result
     */
    private PruningResult checkEarlyTermination(UtilityList utilityList) {
        // Early termination based on itemset size if top-K is full
        if (topKManager.isFull() && utilityList.getItemset().size() > getMaxReasonableItemsetSize()) {
            return PruningResult.pruned(PruningReason.EARLY_TERMINATION,
                "Itemset size exceeds reasonable limit");
        }
        
        // Early termination based on very low probability
        double ep = utilityList.getExistentialProbability();
        if (ep < config.getMinProbability() * 0.1) { // Much lower than threshold
            return PruningResult.pruned(PruningReason.EARLY_TERMINATION,
                "Existential probability extremely low");
        }
        
        return PruningResult.noPruning();
    }
    
    /**
     * Determines a reasonable maximum itemset size based on the problem characteristics.
     * 
     * @return Maximum reasonable itemset size
     */
    private int getMaxReasonableItemsetSize() {
        // Heuristic: limit itemset size based on total number of items
        int totalItems = config.getItemCount();
        return Math.min(10, totalItems / 2); // Reasonable upper bound
    }
    
    /**
     * Checks if a utility-list qualifies as a high-utility itemset.
     * 
     * @param utilityList The utility-list to check
     * @return true if it qualifies, false otherwise
     */
    public boolean qualifiesAsHighUtility(UtilityList utilityList) {
        double expectedUtility = utilityList.getSumExpectedUtility();
        double existentialProbability = utilityList.getExistentialProbability();
        double threshold = topKManager.getThreshold();
        
        return expectedUtility >= threshold - config.getEpsilon() &&
               existentialProbability >= config.getMinProbability() - config.getEpsilon();
    }
    
    /**
     * Estimates the potential utility of extensions for a given utility-list.
     * This can be used for prioritizing search branches.
     * 
     * @param utilityList The utility-list to evaluate
     * @return Estimated potential utility
     */
    public double estimatePotentialUtility(UtilityList utilityList) {
        return utilityList.getSumExpectedUtility() + utilityList.getSumRemainingUtility();
    }
    
    /**
     * Gets comprehensive statistics about the pruning performance.
     * 
     * @return Pruning statistics
     */
    public PruningStatistics getStatistics() {
        long totalPruned = candidatesPrunedByEU + candidatesPrunedByEP + candidatesPrunedByEarlyTermination;
        double prunningRatio = totalCandidatesEvaluated > 0 ? 
            (double) totalPruned / totalCandidatesEvaluated : 0.0;
        
        return new PruningStatistics(
            totalCandidatesEvaluated,
            candidatesPrunedByEU,
            candidatesPrunedByEP,
            candidatesPrunedByEarlyTermination,
            prunningRatio
        );
    }
    
    /**
     * Resets all statistics counters.
     */
    public void resetStatistics() {
        totalCandidatesEvaluated = 0;
        candidatesPrunedByEU = 0;
        candidatesPrunedByEP = 0;
        candidatesPrunedByEarlyTermination = 0;
    }
    
    /**
     * Enumeration of pruning reasons for analysis and debugging.
     */
    public enum PruningReason {
        EXISTENTIAL_PROBABILITY("Existential probability too low"),
        EXPECTED_UTILITY("Expected utility upper bound too low"),
        EARLY_TERMINATION("Early termination heuristic"),
        NONE("No pruning applied");
        
        private final String description;
        
        PruningReason(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Result of a pruning evaluation.
     */
    public static class PruningResult {
        private final boolean shouldPrune;
        private final PruningReason reason;
        private final String details;
        
        private PruningResult(boolean shouldPrune, PruningReason reason, String details) {
            this.shouldPrune = shouldPrune;
            this.reason = reason;
            this.details = details;
        }
        
        public boolean shouldPrune() { return shouldPrune; }
        public PruningReason getReason() { return reason; }
        public String getDetails() { return details; }
        
        public static PruningResult pruned(PruningReason reason, String details) {
            return new PruningResult(true, reason, details);
        }
        
        public static PruningResult noPruning() {
            return new PruningResult(false, PruningReason.NONE, "");
        }
        
        @Override
        public String toString() {
            if (shouldPrune) {
                return String.format("PRUNE[%s: %s]", reason, details);
            } else {
                return "NO_PRUNE";
            }
        }
    }
    
    /**
     * Statistics about pruning effectiveness.
     */
    public static class PruningStatistics {
        private final long totalEvaluated;
        private final long prunedByEU;
        private final long prunedByEP;
        private final long prunedByET;
        private final double pruningRatio;
        
        public PruningStatistics(long totalEvaluated, long prunedByEU, long prunedByEP, 
                                long prunedByET, double pruningRatio) {
            this.totalEvaluated = totalEvaluated;
            this.prunedByEU = prunedByEU;
            this.prunedByEP = prunedByEP;
            this.prunedByET = prunedByET;
            this.pruningRatio = pruningRatio;
        }
        
        public long getTotalEvaluated() { return totalEvaluated; }
        public long getPrunedByEU() { return prunedByEU; }
        public long getPrunedByEP() { return prunedByEP; }
        public long getPrunedByET() { return prunedByET; }
        public long getTotalPruned() { return prunedByEU + prunedByEP + prunedByET; }
        public double getPruningRatio() { return pruningRatio; }
        
        @Override
        public String toString() {
            return String.format("PruningStats[evaluated=%d, pruned=%d (%.2f%%), EU=%d, EP=%d, ET=%d]",
                totalEvaluated, getTotalPruned(), pruningRatio * 100, prunedByEU, prunedByEP, prunedByET);
        }
    }
}
