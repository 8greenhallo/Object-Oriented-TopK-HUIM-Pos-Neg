package ootkhuimunpnu.vntm.join;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.utility.PULList;

/**
 * Strategy interface for the utility-list join operation.
 *
 * <p>A join combines two utility lists — one for prefix A and one for
 * extension B — to produce the utility list for the combined itemset
 * X = A ∪ B.  Different implementations can vary in:
 * <ul>
 *   <li><b>Memory layout</b> — array-based, object-based, compressed</li>
 *   <li><b>Early-exit heuristics</b> — stop scanning when overlap drops
 *       below a useful threshold (e.g., consecutive-miss cutoff)</li>
 *   <li><b>Probability filtering</b> — skip transactions whose joint
 *       probability is already below a useful floor</li>
 *   <li><b>Remaining-utility formula</b> — min vs. sum vs. re-computed</li>
 * </ul>
 *
 * <p>All implementations must preserve the mathematical correctness of:
 * <pre>
 *   u(A∪B, T)     = u(A, T)  + u(B, T)
 *   rpu(A∪B, T)   = min( rpu(A,T), rpu(B,T) )           (conservative upper bound)
 *   log P(A∪B, T) = log P(A, T) + log P(B, T)           (attribute-level independence)
 *   PTWU(A∪B)     = min( PTWU(A), PTWU(B) )             (upper-bound inheritance)
 * </pre>
 *
 * <p>A join that produces a {@code null} result signals to the caller that
 * the candidate was pruned and the subtree rooted at X need not be explored.
 *
 * <p>Implementations must be <b>thread-safe</b> if used inside a parallel
 * search strategy (i.e., they should hold no mutable state, or protect it
 * with appropriate synchronisation).
 *
 * @author Meg
 * @version 5.0
 * 
 * @see SortedMergeJoin
 * @see DiffsetJoin
 * @see BufferedJoin
 */
public interface IJoinStrategy {

    /**
     * Joins two utility lists into a combined list for itemset A ∪ B.
     *
     * <p>Implementations must apply PTWU pruning as the first check:
     * if {@code min(ul1.ptwu, ul2.ptwu) < threshold} the join is guaranteed
     * to produce a list whose EU is below the threshold, so {@code null}
     * should be returned immediately.
     *
     * @param ul1       utility list for prefix A (TID-sorted, ascending)
     * @param ul2       utility list for extension B (TID-sorted, ascending)
     * @param threshold current mining threshold; used for early PTWU pruning
     * @return joined {@link PULList} for A ∪ B, or {@code null} if pruned /
     *         no common transactions found
     */
    PULList join(IUtilityList ul1, IUtilityList ul2, double threshold);
}