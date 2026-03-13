package ootkhuimunpnu.vntm.utility;

import java.util.Set;

/**
 * Strategy interface for utility list data structures.
 *
 * <p>A utility list for itemset X contains one element per transaction T in which X appears.
 * Each element stores:
 * <ul>
 *   <li><b>tid</b>  — transaction ID</li>
 *   <li><b>utility</b>   — u(X,T) = Σ_{i∈X} profit(i)·qty(i,T)</li>
 *   <li><b>remaining</b> — rpu(X,T) = Σ_{i∈T \ X, profit(i)>0} profit(i)·qty(i,T)</li>
 *   <li><b>logProbability</b> — log( P(X,T) ) = Σ_{i∈X} log P(i,T)</li>
 * </ul>
 *
 * <p>The interface exposes aggregate metrics pre-computed at construction:
 * <ul>
 *   <li>{@link #getSumEU()}  — EU(X) = Σ_T u(X,T)·P(X,T)</li>
 *   <li>{@link #getSumRemaining()} — ΣR(X) = Σ_T rpu(X,T)·P(X,T)</li>
 *   <li>{@link #getExistentialProbability()} — EP(X) = 1 - Π_T(1-P(X,T))</li>
 * </ul>
 *
 * <p>Implementations may differ in storage layout (array-based, object-based)
 * but must expose consistent semantics through this interface.
 *
 * @author Meg
 * @version 5.0
 */
public interface IUtilityList {

    /**
     * Returns the itemset associated with this utility list.
     *
     * @return unmodifiable set of item IDs
     */
    Set<Integer> getItemset();

    /**
     * Returns the pre-computed expected utility EU(X).
     * <pre>
     *   EU(X) = Σ_{T: X⊆T}  u(X,T) · P(X,T)
     * </pre>
     * Access is O(1) — value is computed once at construction time.
     *
     * @return EU(X) ≥ 0
     */
    double getSumEU();

    /**
     * Returns the pre-computed expected remaining utility ΣR(X).
     * <pre>
     *   ΣR(X) = Σ_{T: X⊆T}  rpu(X,T) · P(X,T)
     * </pre>
     * Used in the EU-pruning condition: EU(X) + ΣR(X) &lt; threshold → prune.
     *
     * @return expected remaining utility ≥ 0
     */
    double getSumRemaining();

    /**
     * Returns the pre-computed existential probability EP(X).
     * <pre>
     *   EP(X) = 1 - Π_{T: X⊆T} (1 - P(X,T))
     * </pre>
     * Computed in log-space to prevent numerical underflow.
     *
     * @return EP(X) ∈ [0, 1]
     */
    double getExistentialProbability();

    /**
     * Returns the Positive Transaction-Weighted Utility (PTWU) upper bound
     * for this itemset.
     * <pre>
     *   PTWU(X) = Σ_{T: X⊆T}  PTU(T)
     * </pre>
     * PTWU is an upper bound for EU(X) and is used for aggressive pruning.
     *
     * @return PTWU(X) ≥ 0
     */
    double getPtwu();

    /**
     * Returns the number of transactions containing this itemset.
     *
     * @return support count ≥ 0
     */
    int getSize();

    /**
     * Returns {@code true} when no transactions contain this itemset.
     *
     * @return whether the list is empty
     */
    boolean isEmpty();

    // -------------------------------------------------------------------------
    // Array-level access (for join operations — avoids object creation)
    // -------------------------------------------------------------------------

    /**
     * Returns the transaction ID at position {@code index}.
     *
     * @param index element index (0-based)
     * @return transaction ID
     */
    int getTid(int index);

    /**
     * Returns the utility u(X,T) for the element at position {@code index}.
     *
     * @param index element index (0-based)
     * @return utility value
     */
    double getUtility(int index);

    /**
     * Returns the remaining positive utility rpu(X,T) for the element
     * at position {@code index}.
     *
     * @param index element index (0-based)
     * @return remaining utility ≥ 0
     */
    double getRemaining(int index);

    /**
     * Returns the log-probability log(P(X,T)) for the element at
     * position {@code index}.
     *
     * @param index element index (0-based)
     * @return log-probability ≤ 0
     */
    double getLogProbability(int index);
}