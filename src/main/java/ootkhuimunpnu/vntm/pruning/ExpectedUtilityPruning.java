package ootkhuimunpnu.vntm.pruning;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;

/**
 * Pruning strategy focused on the EU + remaining upper bound condition.
 *
 * <p>Emphasises the EU-pruning condition while still applying mandatory
 * structural pruning (PTWU, branch etc.). Use when studying the isolated effect
 * of EU-upper-bound pruning.
 *
 * <pre>
 *   EU(X) + ΣR(X) &lt; threshold  →  X and all supersets cannot be top-K
 * </pre>
 *
 * @author Meg
 * @version 5.0
 */
public class ExpectedUtilityPruning implements IPruningStrategy {

    private final double minProbability;

    /**
     * @param minProbability minimum EP threshold (used for qualifiesForTopK)
     */
    public ExpectedUtilityPruning(double minProbability) {
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

    @Override
    public boolean shouldPruneBulk(double minJoinedPTWU, double threshold) {
        return minJoinedPTWU < threshold - AlgorithmConfig.EPSILON;
    }

    /** Does NOT apply EP pruning — only EU+R. */
    @Override
    public boolean shouldPruneByEP(double existentialProbability) {
        return false; // EP pruning disabled in this variant
    }

    /** Applies EU + remaining upper bound pruning. */
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