package ootkhuimunpnu.vntm.pruning;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;

/**
 * Composite pruning strategy combining all five pruning conditions.
 *
 * <p>This is the default pruning strategy. It applies all conditions in
 * increasing cost order: the cheapest checks (PTWU comparisons) are performed
 * first to fail fast before more expensive EP and EU computations.
 *
 * <p>All methods delegate to the appropriate statistics tracking mechanism
 * via the {@link monitoring.StatisticsCollector} — but since this class
 * is strategy-only (no state), counters are managed by the callers
 * ({@link search.DepthFirstSearch} etc.) who have access to the stats object.
 *
 * @author Meg
 * @version 5.0
 */
public class CompositePruningStrategy implements IPruningStrategy {

    private final double minProbability;

    /**
     * Constructs the composite strategy.
     *
     * @param minProbability minimum existential probability threshold EP(X) ≥ minProbability
     */
    public CompositePruningStrategy(double minProbability) {
        this.minProbability = minProbability;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Condition: PTWU(X) &lt; threshold − ε
     * <p>Complexity: O(1) — simple double comparison.
     */
    @Override
    public boolean shouldPruneByPTWU(double ptwu, double threshold) {
        return ptwu < threshold - AlgorithmConfig.EPSILON;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Same condition as PTWU pruning but for an entire branch rooted at
     * an item. Named separately for semantic clarity and statistics tracking.
     */
    @Override
    public boolean shouldPruneBranch(double itemPTWU, double threshold) {
        return itemPTWU < threshold - AlgorithmConfig.EPSILON;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Condition: min(prefix.ptwu, minExt.ptwu) &lt; threshold.
     * <p>Applied before joining any extension in the batch.
     */
    @Override
    public boolean shouldPruneBulk(double minJoinedPTWU, double threshold) {
        return minJoinedPTWU < threshold - AlgorithmConfig.EPSILON;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Condition: EP(X) &lt; minProbability − ε
     * <p>Complexity: O(1) — EP is pre-computed in the utility list.
     */
    @Override
    public boolean shouldPruneByEP(double existentialProbability) {
        return existentialProbability < minProbability - AlgorithmConfig.EPSILON;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Condition: EU(X) + ΣR(X) &lt; threshold − ε
     * <p>Complexity: O(1) — both aggregates are pre-computed.
     *
     * <p>This is the tightest upper bound pruning condition available without
     * additional computation.
     */
    @Override
    public boolean shouldPruneByEU(double sumEU, double sumRemaining, double threshold) {
        return (sumEU + sumRemaining) < threshold - AlgorithmConfig.EPSILON;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Condition: EU(X) ≥ threshold AND EP(X) ≥ minProbability
     */
    @Override
    public boolean qualifiesForTopK(IUtilityList ul, double threshold) {
        return ul.getSumEU() >= threshold - AlgorithmConfig.EPSILON
            && ul.getExistentialProbability() >= minProbability - AlgorithmConfig.EPSILON;
    }
}