package ootkhuimunpnu.vntm.pruning;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;

/**
 * Pruning strategy focused on the Existential Probability constraint.
 *
 * <p>This strategy enables/disables EP-based pruning while still applying
 * the mandatory PTWU and EU+remaining conditions for correctness.
 * Use when studying the isolated effect of EP pruning on mining performance.
 *
 * <pre>
 *   EP(X) = 1 - Π_{T∈D(X)} (1 - P(X,T))
 *   Prune if EP(X) < minProbability
 * </pre>
 *
 * @author Meg
 * @version 5.0
 */
public class ExistentialProbabilityPruning implements IPruningStrategy {

    private final double minProbability;

    /**
     * @param minProbability minimum EP threshold
     */
    public ExistentialProbabilityPruning(double minProbability) {
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

    /** Applies EP pruning: prune if EP(X) &lt; minProbability. */
    @Override
    public boolean shouldPruneByEP(double existentialProbability) {
        return existentialProbability < minProbability - AlgorithmConfig.EPSILON;
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