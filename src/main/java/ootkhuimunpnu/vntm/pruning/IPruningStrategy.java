package ootkhuimunpnu.vntm.pruning;

import ootkhuimunpnu.vntm.utility.IUtilityList;

/**
 * Strategy interface for pruning decisions in the HUIM search.
 *
 * <p>Five pruning conditions correspond to the five major ways a candidate
 * itemset can be eliminated from consideration:
 *
 * <ol>
 *   <li><b>PTWU pruning</b> — PTWU(X) &lt; threshold</li>
 *   <li><b>Branch pruning</b> — entire item branch is dominated</li>
 *   <li><b>Bulk branch pruning</b> — batch of extensions all dominated</li>
 *   <li><b>EP pruning</b> — EP(X) &lt; minProbability</li>
 *   <li><b>EU + remaining pruning</b> — EU(X) + ΣR(X) &lt; threshold</li>
 * </ol>
 *
 * <p>Implementations must be stateless (or thread-safe) since they may be
 * called concurrently from multiple threads.
 *
 * @author Meg
 * @version 5.0
 */
public interface IPruningStrategy {

    /**
     * Prunes a candidate itemset based on its PTWU upper bound.
     *
     * <pre>
     *   Theorem: EU(X) ≤ PTWU(X)
     *   If PTWU(X) < threshold → EU(X) < threshold → X cannot be top-K
     * </pre>
     *
     * @param ptwu      PTWU(X) upper bound
     * @param threshold current mining threshold
     * @return {@code true} if X should be pruned
     */
    boolean shouldPruneByPTWU(double ptwu, double threshold);

    /**
     * Prunes an entire prefix branch when the item's PTWU is too low.
     *
     * <pre>
     *   If PTWU(item) < threshold → X and all supersets containing item → prune
     * </pre>
     *
     * @param itemPTWU  PTWU of the root item of this branch
     * @param threshold current mining threshold
     * @return {@code true} if the branch should be pruned entirely
     */
    boolean shouldPruneBranch(double itemPTWU, double threshold);

    /**
     * Prunes a batch of extensions when the minimum PTWU across all
     * candidates is below threshold.
     *
     * <pre>
     *   minPTWU = min(PTWU(e) | e ∈ extensions)
     *   If min(prefix.ptwu, minPTWU) < threshold → no extension can qualify
     * </pre>
     *
     * @param minJoinedPTWU minimum PTWU among all joined candidates
     * @param threshold     current mining threshold
     * @return {@code true} if the entire extension batch should be skipped
     */
    boolean shouldPruneBulk(double minJoinedPTWU, double threshold);

    /**
     * Prunes an itemset by existential probability.
     *
     * <pre>
     *   If EP(X) < minProbability → X does not meet the existence constraint
     * </pre>
     *
     * @param existentialProbability EP(X)
     * @return {@code true} if X should be pruned
     */
    boolean shouldPruneByEP(double existentialProbability);

    /**
     * Prunes an itemset using the EU + remaining upper bound.
     *
     * <pre>
     *   EU(X) + ΣR(X) is an upper bound for EU of any superset of X.
     *   If EU(X) + ΣR(X) < threshold → X and all its supersets cannot be top-K
     * </pre>
     *
     * @param sumEU         EU(X)
     * @param sumRemaining  ΣR(X)
     * @param threshold     current mining threshold
     * @return {@code true} if X and its supersets should be pruned
     */
    boolean shouldPruneByEU(double sumEU, double sumRemaining, double threshold);

    /**
     * Determines whether a utility list qualifies for inclusion in top-K.
     *
     * <pre>
     *   Qualifies if: EU(X) ≥ threshold AND EP(X) ≥ minProbability
     * </pre>
     *
     * @param ul        utility list of candidate X
     * @param threshold current mining threshold
     * @return {@code true} if X should be added to the top-K set
     */
    boolean qualifiesForTopK(IUtilityList ul, double threshold);
}