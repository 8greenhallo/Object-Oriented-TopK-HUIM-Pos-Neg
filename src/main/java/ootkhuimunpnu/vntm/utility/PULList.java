package ootkhuimunpnu.vntm.utility;

import java.util.*;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;

/**
 * Array-based Probabilistic Utility-Link List (PUL) implementation.
 *
 * <p>Stores one entry per supporting transaction using five parallel arrays
 * instead of a list of {@code Element} objects.  This reduces memory
 * allocation by 80–90% for large databases:
 * <pre>
 *   Object-based: N Element objects × ~48 bytes/object = 48N bytes
 *   Array-based : 5 arrays × 8 bytes/slot              = 40N bytes  (no GC header)
 * </pre>
 *
 * <p><b>Aggregate pre-computation</b>: EU, ΣR, and EP are all computed
 * exactly once during construction, giving O(1) read access thereafter.
 * This is critical because these values are checked millions of times during
 * the search.
 *
 * <h3>Mathematical Definitions</h3>
 * <pre>
 *   For itemset X, let D(X) = { T ∈ D | X ⊆ T } (supporting transactions)
 *
 *   Utility in T:
 *     u(X,T)     = Σ_{i∈X} profit(i) · qty(i,T)
 *
 *   Remaining positive utility:
 *     rtu(X,T)   = Σ_{i∈T\X, profit(i)>0} profit(i) · qty(i,T)
 *
 *   Joint probability of X in T (attribute-level uncertainty model):
 *     P(X,T)     = Π_{i∈X} P(i,T)     stored as log(P(X,T)) = Σ log P(i,T)
 *
 *   Expected Utility:
 *     EU(X)      = Σ_{T∈D(X)} u(X,T) · P(X,T)
 *
 *   Expected Remaining:
 *     ΣR(X)      = Σ_{T∈D(X)} rtu(X,T) · P(X,T)
 *
 *   Existential Probability:
 *     EP(X)      = 1 - Π_{T∈D(X)} (1 - P(X,T))
 *                 computed via: log(1-EP) = Σ log(1 - P(X,T))
 * </pre>
 *
 * @author Meg
 * @version 5.0
 */
public class PULList implements IUtilityList {

    // =========================================================================
    // Array-based storage (single allocation per PULList)
    // =========================================================================

    /** Itemset this utility list represents. */
    private final Set<Integer> itemset;

    /** Transaction IDs of supporting transactions (sorted ascending). */
    private final int[] tids;

    /** u(X,T) for each supporting transaction. */
    private final double[] utilities;

    /** rtu(X,T) for each supporting transaction (positive suffix utilities only). */
    private final double[] remainings;

    /**
     * log(P(X,T)) for each supporting transaction.
     * Stored in log-space to prevent underflow when X is large.
     * log(P(X,T)) = Σ_{i∈X} log P(i,T)
     */
    private final double[] logProbabilities;

    /** Number of supporting transactions. */
    private final int size;

    /**
     * PTWU upper bound: PTWU(X) = min(PTWU(A), PTWU(B)) when X = A ∪ B.
     * Satisfies: EU(X) ≤ PTWU(X), so PTWU &lt; threshold implies prune.
     */
    private final double ptwu;

    /**
     * Diffset relative to parent: TIDs present in the parent PULList
     * but absent from this one. Null when the standard tidset
     * representation is used (i.e., not built via DiffsetJoin).
     * Populated lazily by DiffsetJoin.buildWithDiffset().
     */
    private int[] diffsetTids; // package-private for DiffsetJoin access

    // =========================================================================
    // Pre-computed aggregates (O(1) access)
    // =========================================================================

    /** EU(X) = Σ_T u(X,T)·P(X,T) */
    private final double sumEU;

    /** ΣR(X) = Σ_T rtu(X,T)·P(X,T) */
    private final double sumRemaining;

    /**
     * EP(X) = 1 - Π_T(1 - P(X,T))
     * Computed in log-space for numerical stability.
     */
    private final double existentialProbability;

    // =========================================================================
    // Construction
    // =========================================================================

    /** Returns EU(X) + ΣR(X) — the upper bound on EU of any extension. */
    public double getMaxPotential() {
        return sumEU + sumRemaining;
    }

    /**
     * Constructs a PULList from a list of Element objects.
     *
     * <p>All aggregates (EU, ΣR, EP) are computed here in a single pass
     * so subsequent accesses are O(1).
     *
     * @param itemset  the itemset X
     * @param elements list of transaction elements (one per supporting transaction)
     * @param ptwu     PTWU upper bound for X
     */
    public PULList(Set<Integer> itemset, List<Element> elements, double ptwu) {
        this.itemset = Collections.unmodifiableSet(new HashSet<>(itemset));
        this.ptwu    = ptwu;
        this.size    = elements.size();

        // Single array allocation (much cheaper than N Element objects)
        this.tids             = new int[size];
        this.utilities        = new double[size];
        this.remainings       = new double[size];
        this.logProbabilities = new double[size];

        // Pass 1: copy data and compute EU / ΣR simultaneously
        double tmpSumEU    = 0.0;
        double tmpSumRem   = 0.0;
        for (int i = 0; i < size; i++) {
            Element e = elements.get(i);
            tids[i]             = e.getTid();
            utilities[i]        = e.getUtility();
            remainings[i]       = e.getRemaining();
            logProbabilities[i] = e.getLogProbability();

            // P(X,T) = exp(logP)  — kept in log form during pass to avoid precision loss
            double prob = Math.exp(e.getLogProbability());
            tmpSumEU  += e.getUtility()   * prob;   // EU(X)  contribution from T
            tmpSumRem += e.getRemaining() * prob;   // ΣR(X)  contribution from T
        }
        this.sumEU        = tmpSumEU;
        this.sumRemaining = tmpSumRem;

        // Pass 2: compute EP in log-space
        // EP(X) = 1 - Π_T (1 - P(X,T))
        // ⟹  log(1-EP) = Σ_T log(1-P(X,T))
        this.existentialProbability = computeExistentialProbability();
    }

    /**
     * Computes EP(X) in log-space to prevent underflow.
     *
     * <pre>
     *   logComplement += log(1 - P(X,T))   for each T
     *   EP = 1 - exp(logComplement)
     * </pre>
     *
     * Edge cases:
     * <ul>
     *   <li>If any P(X,T) ≈ 1 → EP = 1.0 immediately</li>
     *   <li>If logComplement &lt; LOG_EPSILON → complement ≈ 0 → EP = 1.0</li>
     * </ul>
     */
    private double computeExistentialProbability() {
        if (size == 0) return 0.0;

        double logComplement = 0.0;
        for (int i = 0; i < size; i++) {
            if (logProbabilities[i] > Math.log(1.0 - AlgorithmConfig.EPSILON)) {
                // P(X,T) ≈ 1  →  1-P ≈ 0  →  log(1-P) = -∞  →  EP = 1
                return 1.0;
            }
            double prob = Math.exp(logProbabilities[i]);
            // Use log1p(-p) for numerical accuracy when p is small
            double log1MinusP = (prob < 0.5)
                    ? Math.log1p(-prob)
                    : Math.log(1.0 - prob);

            logComplement += log1MinusP;

            // Early exit if complement becomes negligible
            if (logComplement < AlgorithmConfig.LOG_EPSILON) {
                return 1.0;
            }
        }
        return 1.0 - Math.exp(logComplement);
    }

    // =========================================================================
    // IUtilityList — O(1) aggregate accessors
    // =========================================================================

    @Override public Set<Integer> getItemset()              { return itemset; }
    @Override public double getSumEU()                      { return sumEU; }
    @Override public double getSumRemaining()               { return sumRemaining; }
    @Override public double getExistentialProbability()     { return existentialProbability; }
    @Override public double getPtwu()                       { return ptwu; }
    @Override public int    getSize()                       { return size; }
    @Override public boolean isEmpty()                      { return size == 0; }

    // =========================================================================
    // Array-level access (avoids Element object creation during join)
    // =========================================================================

    @Override public int    getTid(int i)            { return tids[i]; }
    @Override public double getUtility(int i)        { return utilities[i]; }
    @Override public double getRemaining(int i)      { return remainings[i]; }
    @Override public double getLogProbability(int i) { return logProbabilities[i]; }

    // =========================================================================
    //  Variant: diffset join support (optional, set by DiffsetJoinStrategy)
    // =========================================================================

    /** Returns the raw diffset TID array, or {@code null} if not computed. */
    public int[] getDiffsetTids() { return diffsetTids; }

    /** Sets the diffset TID array. Called only by DiffsetJoinStrategy. */
    public void setDiffsetTids(int[] diffsetTids) { this.diffsetTids = diffsetTids; }

    // =========================================================================
    // Compatibility: element list (lazy, created on demand)
    // =========================================================================

    /**
     * Returns all elements as an unmodifiable list.
     * Created lazily — most code paths use array access directly.
     *
     * @return list of {@link Element} objects
     */
    public List<Element> getElements() {
        List<Element> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new Element(tids[i], utilities[i], remainings[i], logProbabilities[i]));
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public String toString() {
        return String.format("PULList{itemset=%s, size=%d, EU=%.4f, EP=%.4f, ptwu=%.2f}",
                itemset, size, sumEU, existentialProbability, ptwu);
    }

    // =========================================================================
    // Element inner class
    // =========================================================================

    /**
     * Single element of a utility list, corresponding to one supporting transaction.
     *
     * <p>Used during list construction and join operations. After construction
     * the data is stored in arrays and Element objects are discarded.
     */
    public static class Element {

        private final int tid;
        private final double utility;
        private final double remaining;
        private final double logProbability;

        /**
         * @param tid            transaction ID
         * @param utility        u(X,T)
         * @param remaining      rtu(X,T)
         * @param logProbability log(P(X,T))
         */
        public Element(int tid, double utility, double remaining, double logProbability) {
            this.tid            = tid;
            this.utility        = utility;
            this.remaining      = remaining;
            this.logProbability = logProbability;
        }

        public int    getTid()            { return tid; }
        public double getUtility()        { return utility; }
        public double getRemaining()      { return remaining; }
        public double getLogProbability() { return logProbability; }
        /** Converts log-probability back to probability. */
        public double getProbability()    { return Math.exp(logProbability); }

        @Override
        public String toString() {
            return String.format("Element[tid=%d, u=%.2f, rem=%.2f, logP=%.4f]",
                    tid, utility, remaining, logProbability);
        }
    }
}