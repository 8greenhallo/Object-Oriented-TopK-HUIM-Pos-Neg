package ootkhuimunpnu.ver02.engine;

import ootkhuimunpnu.ver02.core.UtilityList;

/**
 * Pruning strategies for the mining algorithm
 * 
 * @author Meg
 * @version 2.0
 */
public class PruningStrategy {
    private final Configuration config;
    
    // Pruning statistics
    private long euPruned = 0;
    private long epPruned = 0;
    private long totalPruned = 0;

    public PruningStrategy(Configuration config) {
        this.config = config;
    }

    /**
     * Check if a utility list should be pruned
     * @param ul the utility list to check
     * @param currentThreshold the current top-k threshold
     * @return true if the utility list should be pruned
     */
    public boolean shouldPrune(UtilityList ul, double currentThreshold) {
        if (ul == null || ul.isEmpty()) {
            return true;
        }

        // Pruning Strategy 1: Existential probability
        if (ul.getExistentialProbability() < config.getMinProbability() - config.getEpsilon()) {
            epPruned++;
            totalPruned++;
            return true;
        }

        // Pruning Strategy 2: EU + remaining utility upper bound
        if (ul.getSumEU() + ul.getSumRemaining() < currentThreshold - config.getEpsilon()) {
            euPruned++;
            totalPruned++;
            return true;
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

    // Statistics methods
    public long getEuPruned() {
        return euPruned;
    }

    public long getEpPruned() {
        return epPruned;
    }

    public long getTotalPruned() {
        return totalPruned;
    }

    public void resetStatistics() {
        euPruned = 0;
        epPruned = 0;
        totalPruned = 0;
    }

    public String getStatistics() {
        return String.format("Pruning Statistics:\n - Total: %d\n - EU+Remaining pruned: %d\n - Existential Probability pruned: %d",
                           totalPruned, euPruned, epPruned);
    }
}