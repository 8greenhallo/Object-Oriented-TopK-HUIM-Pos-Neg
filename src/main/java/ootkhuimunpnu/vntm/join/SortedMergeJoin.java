package ootkhuimunpnu.vntm.join;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.utility.PULList;

import java.util.*;

/**
 * Standard sorted-merge join — the canonical {@link IJoinStrategy} implementation.
 *
 * <p>This is a direct OOP extraction of the join logic from {@code ver11_1.java}
 * and the original {@code MiningEngine.java}.  It performs a linear sorted-merge
 * on the two TID-sorted utility lists, processing every matching transaction
 * from start to finish.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>PTWU bound check: {@code min(PTWU(A), PTWU(B)) < threshold} → return null.</li>
 *   <li>Two-pointer merge over TID arrays (both ascending).</li>
 *   <li>For each common TID, combine utility, remaining, and log-probability.</li>
 *   <li>Skip elements whose joint log-probability ≤ LOG_EPSILON.</li>
 *   <li>Return {@code null} if no common transactions remain.</li>
 * </ol>
 *
 * <h3>Mathematical Invariants</h3>
 * <pre>
 *   u(A∪B, T)     = u(A,T)   + u(B,T)
 *   rtu(A∪B, T)   = min( rtu(A,T), rtu(B,T) )      conservative remaining upper bound
 *   log P(A∪B, T) = log P(A,T) + log P(B,T)         attribute-level independence
 *   PTWU(A∪B)     = min( PTWU(A), PTWU(B) )
 * </pre>
 *
 * <h3>Complexity</h3>
 * <pre>
 *   Time : O( |ul1| + |ul2| )  — single pass, two pointers
 *   Space: O( min(|ul1|, |ul2|) )  — at most min(size1, size2) elements in result
 * </pre>
 *
 * //<p>Use this as the baseline for benchmarking against {@link EarlyTerminationJoin}
 * and {@link ProbabilityFilteredJoin}.
 *
 * @author Meg
 * @version 5.0
 * 
 * //@see PULListJoinOperation  standard tidset join (better for sparse data)
 * @see IJoinStrategy         strategy interface this class implements
 * @see DiffsetJoin           alternative diffset-based join (better for dense data)
 * @see BufferedJoin          buffered join with early-exit heuristics
 */
public class SortedMergeJoin implements IJoinStrategy {

    /**
     * Constructs a PULListJoin. Stateless — safe for concurrent use.
     */
    public SortedMergeJoin() {}

    /**
     * {@inheritDoc}
     *
     * <p>Performs a complete sorted-merge from start to finish.
     * No early exit on consecutive misses — see {@link EarlyTerminationJoin}
     * for that optimisation.
     */
    @Override
    public PULList join(IUtilityList ul1, IUtilityList ul2, double threshold) {

        // -----------------------------------------------------------------
        // Guard 1: PTWU(A∪B) = min(PTWU(A), PTWU(B))
        // EU(A∪B) ≤ PTWU(A∪B) → if PTWU < threshold, prune entire subtree
        // -----------------------------------------------------------------
        double joinedPTWU = Math.min(ul1.getPtwu(), ul2.getPtwu());
        if (joinedPTWU < threshold - AlgorithmConfig.EPSILON) {
            return null;
        }

        int size1 = ul1.getSize();
        int size2 = ul2.getSize();
        if (size1 == 0 || size2 == 0) return null;

        // -----------------------------------------------------------------
        // Two-pointer sorted-merge on TID (both lists are TID-ascending)
        // -----------------------------------------------------------------
        int estimatedCapacity = Math.min(size1, size2);
        List<PULList.Element> elements = new ArrayList<>(estimatedCapacity);

        int i = 0, j = 0;
        while (i < size1 && j < size2) {
            int tid1 = ul1.getTid(i);
            int tid2 = ul2.getTid(j);

            if (tid1 == tid2) {
                // Common transaction T found — combine the three fields

                // u(A∪B, T) = u(A,T) + u(B,T)
                double newUtility = ul1.getUtility(i) + ul2.getUtility(j);

                // rtu(A∪B, T) = min(rtu(A,T), rtu(B,T))
                // Taking the minimum is conservative: the remaining utility of a
                // larger itemset can only be ≤ that of either subset alone.
                double newRemaining = Math.min(ul1.getRemaining(i), ul2.getRemaining(j));

                // log P(A∪B, T) = log P(A,T) + log P(B,T)   [independence]
                double newLogProb = ul1.getLogProbability(i) + ul2.getLogProbability(j);

                // Skip if joint probability is negligibly small
                if (newLogProb > AlgorithmConfig.LOG_EPSILON) {
                    elements.add(new PULList.Element(tid1, newUtility, newRemaining, newLogProb));
                }
                i++;
                j++;

            } else if (tid1 < tid2) {
                i++;    // T not in ul2 — advance ul1 pointer
            } else {
                j++;    // T not in ul1 — advance ul2 pointer
            }
        }

        if (elements.isEmpty()) return null;

        // Build itemset X = A ∪ B
        Set<Integer> newItemset = new HashSet<>(ul1.getItemset());
        newItemset.addAll(ul2.getItemset());

        return new PULList(newItemset, elements, joinedPTWU);
    }
}