package ootkhuimunpnu.vntm.pruning;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;

/**
 * Pruning strategy that emphasises bulk branch pruning.
 *
 * <p>Applies all five conditions with special attention to the bulk-branch
 * pruning step, which eliminates entire batches of extensions in a single
 * check. Use when studying the incremental benefit of bulk pruning.
 *
 * @author Meg
 * @version 5.0
 */
public class BulkPruningStrategy implements IPruningStrategy {

    private final double minProbability;

    /**
     * @param minProbability minimum EP threshold
     */
    public BulkPruningStrategy(double minProbability) {
        this.minProbability = minProbability;
    }

    @Override
    public boolean shouldPruneByPTWU(double ptwu, double threshold) {
        return ptwu < threshold - AlgorithmConfig.EPSILON;
    }

    @Override
    public boolean shouldPruneBranch(double itemPTWU, double threshold) {
        return itemPTWU < threshold - AlgorithmConfig.EPSILON;
    }

    /**
     * Core feature of this strategy: bulk branch pruning.
     * <pre>
     *   If min(prefix.ptwu, minExtPTWU) < threshold →
     *   no extension can ever produce a top-K itemset → prune all.
     * </pre>
     */
    @Override
    public boolean shouldPruneBulk(double minJoinedPTWU, double threshold) {
        return minJoinedPTWU < threshold - AlgorithmConfig.EPSILON;
    }

    @Override
    public boolean shouldPruneByEP(double existentialProbability) {
        return existentialProbability < minProbability - AlgorithmConfig.EPSILON;
    }

    @Override
    public boolean shouldPruneByEU(double sumEU, double sumRemaining, double threshold) {
        return (sumEU + sumRemaining) < threshold - AlgorithmConfig.EPSILON;
    }

    @Override
    public boolean qualifiesForTopK(IUtilityList ul, double threshold) {
        return ul.getSumEU() >= threshold - AlgorithmConfig.EPSILON
            && ul.getExistentialProbability() >= minProbability - AlgorithmConfig.EPSILON;
    }
}