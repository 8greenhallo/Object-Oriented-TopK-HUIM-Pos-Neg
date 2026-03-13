package ootkhuimunpnu.vntm.join;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.utility.PULList;
import java.util.*;

/**
 * Diffset-based join strategy for Probabilistic Utility Lists (PUL).
 *
 * <h3>Theoretical Background — Paper: DCHUIM (DUL structure)</h3>
 * <p>Classical utility-list mining (HMiner, FHM) stores the full <em>tidset</em>
 * T(X) = { t ∈ D | X ⊆ t } for every itemset X.  For dense databases this is
 * expensive: a frequent itemset appearing in 90% of transactions stores 90% of
 * all TIDs.
 *
 * <p>The DCHUIM paper proposes a <b>DUL (Diffset Utility List)</b> that stores
 * instead the <em>diffset</em>:
 * <pre>
 *   DiffSet(X, P) = TidSet(P) \ TidSet(X)
 * </pre>
 * where P is the immediate prefix (parent) of X in the enumeration tree.
 * Since TidSet(X) ⊆ TidSet(P), the diffset captures only the TIDs that
 * P covers but X does not — far smaller for dense data.
 *
 * <h3>Join Rule (DCHUIM Algorithm 3, adapted for uncertainty)</h3>
 * <p>Given two siblings A, B sharing parent P:
 * <pre>
 *   DiffSet(A∪B, A) = DiffSet(B, P) \ DiffSet(A, P)
 *                   = { t | t ∈ DiffSet(B,P)  AND  t ∉ DiffSet(A,P) }
 *
 *   TidSet(A∪B)     = TidSet(A) \ DiffSet(A∪B, A)
 * </pre>
 *
 * <h3>Probabilistic Extension (our contribution)</h3>
 * <p>We extend DCHUIM's deterministic join to the uncertain setting by
 * preserving all three per-entry fields of {@link PULList}:
 * <pre>
 *   u(A∪B, t)      = u(A, t)   + u(B, t)               [utility sum]
 *   rtu(A∪B, t)    = min(rtu(A, t), rtu(B, t))          [conservative remaining]
 *   logP(A∪B, t)   = logP(A, t) + logP(B, t)            [log-probability, independence]
 * </pre>
 * Entries whose joint log-probability falls below {@link AlgorithmConfig#LOG_EPSILON}
 * are treated as having negligible existence probability and excluded from the result
 * (their TIDs contribute to the new diffset instead).
 *
 * <h3>Pruning Strategies (translated from DCHUIM)</h3>
 * <ul>
 *   <li><b>TWU-Prune (U-Prune)</b>: if {@code min(PTWU(A), PTWU(B)) < threshold},
 *       return {@code null} immediately — no extension of A∪B can be useful.</li>
 *   <li><b>Empty-result pruning</b>: if the join yields no valid elements, return {@code null}.</li>
 *   <li><b>Log-probability filtering</b>: entries with {@code logP ≤ LOG_EPSILON} are dropped.</li>
 * </ul>
 *
 * <h3>Two Execution Paths</h3>
 * <ol>
 *   <li><b>Fast diffset path</b>: both inputs carry precomputed diffsets
 *       ({@code PULList.getDiffsetTids() != null}) → O(|diffset|) join via
 *       set subtraction — the core DCHUIM innovation.</li>
 *   <li><b>Fallback sorted-merge path</b>: one or both inputs lack a diffset
 *       (e.g., first-level 1-itemsets built by {@link PULListBuilder}) →
 *       standard two-pointer merge identical to {@link SortedMergeJoin}, but
 *       additionally computes and attaches a diffset to the result so that
 *       subsequent recursive levels automatically use the fast path.</li>
 * </ol>
 *
 * <h3>When to Prefer This Strategy</h3>
 * <table border="1">
 *   <tr><th>Database characteristic</th><th>Recommended strategy</th></tr>
 *   <tr><td>Dense (avg transaction length &gt; 20, high support items)</td>
 *       <td>{@code DiffsetJoin} — diffsets are tiny vs. full tidsets</td></tr>
 *   <tr><td>Sparse (avg transaction length &lt; 10)</td>
 *       <td>{@link SortedMergeJoin} — tidsets are already small; diffset overhead not worth it</td></tr>
 * </table>
 *
 * <h3>Usage in ExperimentRunner</h3>
 * <pre>
 *   // Swap PULListJoin for DiffsetJoin in ExperimentRunner.java line ~115:
 *   IJoinStrategy joinStrategy = new DiffsetJoin();
 *   DepthFirstSearch dfs = new DepthFirstSearch(joinStrategy);
 * </pre>
 *
 * <h3>Prerequisite: PULList.setDiffsetTids visibility</h3>
 * <p>This class calls {@code PULList.setDiffsetTids()} which must be
 * {@code public} (not package-private). Add or verify:
 * <pre>
 *   // In PULList.java line ~225 — change to public:
 *   public void setDiffsetTids(int[] diffsetTids) { this.diffsetTids = diffsetTids; }
 * </pre>
 *
 * @author Meg
 * @version 5.0
 * @see IJoinStrategy strategy interface this class implements
 * @see SortedMergeJoin   standard tidset join (better for sparse data)
 * @see BufferedJoin    buffered join with early-exit heuristics
 */
public class DiffsetJoin implements IJoinStrategy {

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Constructs a {@code DiffsetJoin}. Stateless — safe for concurrent use.
     */
    public DiffsetJoin() {}

    // =========================================================================
    // IJoinStrategy — primary entry point
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Executes the DCHUIM-style diffset join:
     * <ol>
     *   <li>TWU-Prune: {@code min(PTWU(A), PTWU(B)) < threshold} → return {@code null}.</li>
     *   <li>Guard empty inputs.</li>
     *   <li>If both inputs have precomputed diffsets → {@link #joinViaDiffsets} (fast path).</li>
     *   <li>Otherwise → {@link #joinViaSortedMerge} (fallback, builds diffset for next level).</li>
     * </ol>
     *
     * @param ul1       utility list for prefix A (must be TID-sorted ascending)
     * @param ul2       utility list for extension B (must be TID-sorted ascending)
     * @param threshold current top-K utility threshold (corresponds to {@code minUtil} in paper)
     * @return joined {@link PULList} for A∪B with diffset attached, or {@code null} if pruned
     */
    @Override
    public PULList join(IUtilityList ul1, IUtilityList ul2, double threshold) {

        // ------------------------------------------------------------------
        // Step 1: TWU-Prune / U-Prune
        //   Paper: if TWU(A∪B) = min(TWU(A), TWU(B)) < minUtil → prune
        //   Here : PTWU ≈ TWU (probabilistic transaction-weighted utility)
        // ------------------------------------------------------------------
        double joinedPTWU = Math.min(ul1.getPtwu(), ul2.getPtwu());
        if (joinedPTWU < threshold - AlgorithmConfig.EPSILON) {
            return null;  // entire subtree pruned
        }

        // ------------------------------------------------------------------
        // Step 2: Guard — empty inputs cannot produce a valid join
        // ------------------------------------------------------------------
        if (ul1.getSize() == 0 || ul2.getSize() == 0) {
            return null;
        }

        // ------------------------------------------------------------------
        // Step 3: Select join path based on diffset availability
        //   Both inputs must be PULList instances with non-null diffsets
        //   for the DCHUIM fast path; otherwise fall back to sorted merge.
        // ------------------------------------------------------------------
        boolean ul1HasDiffset = (ul1 instanceof PULList)
                && ((PULList) ul1).getDiffsetTids() != null;
        boolean ul2HasDiffset = (ul2 instanceof PULList)
                && ((PULList) ul2).getDiffsetTids() != null;

        if (ul1HasDiffset && ul2HasDiffset) {
            // Fast path: O(|diffset|) — core DCHUIM Algorithm 3
            return joinViaDiffsets((PULList) ul1, (PULList) ul2, joinedPTWU);
        } else {
            // Fallback: O(|tidset|) — builds diffset so next level uses fast path
            return joinViaSortedMerge(ul1, ul2, joinedPTWU);
        }
    }

    // =========================================================================
    // Fast path: DCHUIM Algorithm 3 — both inputs carry diffsets
    // =========================================================================

    /**
     * Diffset join when both ul1 (itemset A) and ul2 (itemset B) store their
     * diffsets relative to their shared prefix P.
     *
     * <h4>Mathematical derivation (DCHUIM §3.2)</h4>
     * <pre>
     *   TidSet(A)     = TidSet(P) \ DiffSet(A, P)
     *   TidSet(B)     = TidSet(P) \ DiffSet(B, P)
     *   TidSet(A∪B)   = TidSet(A) ∩ TidSet(B)
     *                 = TidSet(P) \ (DiffSet(A,P) ∪ DiffSet(B,P))
     *
     *   DiffSet(A∪B, A) = TidSet(A) \ TidSet(A∪B)
     *                   = DiffSet(B, P) \ DiffSet(A, P)
     *
     *   TidSet(A∪B)   = TidSet(A) \ DiffSet(A∪B, A)
     * </pre>
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>Compute {@code newDiffset = DiffSet(B,P) \ DiffSet(A,P)} via set subtraction.</li>
     *   <li>Build a TID-to-index lookup over ul2 (B) in O(|B|) time.</li>
     *   <li>Iterate ul1 (A): for each TID not in {@code newDiffset}, look up B and combine.</li>
     *   <li>Attach {@code newDiffset} to the result for subsequent recursive levels.</li>
     * </ol>
     *
     * @param ul1        PULList for A, with {@code getDiffsetTids()} relative to parent P
     * @param ul2        PULList for B, with {@code getDiffsetTids()} relative to parent P
     * @param joinedPTWU pre-computed {@code min(PTWU(A), PTWU(B))}
     * @return joined PULList for A∪B with new diffset attached, or {@code null} if empty
     */
    private PULList joinViaDiffsets(PULList ul1, PULList ul2, double joinedPTWU) {

        int[] diffA = ul1.getDiffsetTids(); // DiffSet(A, P): TIDs in P but not in A
        int[] diffB = ul2.getDiffsetTids(); // DiffSet(B, P): TIDs in P but not in B

        // ------------------------------------------------------------------
        // Step 1: DiffSet(A∪B, A) = DiffSet(B, P) \ DiffSet(A, P)
        //   = TIDs that B misses (diffB) but A has (not in diffA)
        // ------------------------------------------------------------------
        Set<Integer> diffASet = toIntSet(diffA);
        List<Integer> newDiffsetList = new ArrayList<>(diffB.length);
        for (int tid : diffB) {
            if (!diffASet.contains(tid)) {
                newDiffsetList.add(tid);
            }
        }
        Set<Integer> newDiffsetSet = new HashSet<>(newDiffsetList);

        // ------------------------------------------------------------------
        // Step 2: Build TID → row-index map for ul2 (B) — O(|B|) space
        //   We need u(B,t), rtu(B,t), logP(B,t) for each TID in TidSet(A∪B)
        // ------------------------------------------------------------------
        int size2 = ul2.getSize();
        Map<Integer, Integer> ul2ByTid = new HashMap<>(size2 * 2);
        for (int j = 0; j < size2; j++) {
            ul2ByTid.put(ul2.getTid(j), j);
        }

        // ---------------------------------------------------------------------
        // Step 3: Iterate TidSet(A) — skip TIDs in newDiffset, combine the rest
        // ---------------------------------------------------------------------
        int size1 = ul1.getSize();
        List<PULList.Element> elements = new ArrayList<>(size1 - newDiffsetList.size());

        for (int i = 0; i < size1; i++) {
            int tid = ul1.getTid(i);

            // TID is in DiffSet(A∪B, A) → not in TidSet(A∪B) → skip
            if (newDiffsetSet.contains(tid)) {
                continue;
            }

            Integer idx2 = ul2ByTid.get(tid);
            if (idx2 == null) {
                // Defensive: shouldn't happen when diffsets are consistent
                continue;
            }

            // Combine fields per the probabilistic extension:
            double newUtility   = ul1.getUtility(i)      + ul2.getUtility(idx2);
            double newRemaining = Math.min(ul1.getRemaining(i), ul2.getRemaining(idx2));
            double newLogProb   = ul1.getLogProbability(i) + ul2.getLogProbability(idx2);

            // Log-probability filter: entries with negligible joint probability are dropped
            // (their TIDs are implicitly absent from the result — already excluded above)
            if (newLogProb > AlgorithmConfig.LOG_EPSILON) {
                elements.add(new PULList.Element(tid, newUtility, newRemaining, newLogProb));
            }
        }

        if (elements.isEmpty()) {
            return null;
        }

        // ------------------------------------------------------------------
        // Step 4: Build result PULList and attach new diffset
        // ------------------------------------------------------------------
        Set<Integer> newItemset = new HashSet<>(ul1.getItemset());
        newItemset.addAll(ul2.getItemset());

        PULList result = new PULList(newItemset, elements, joinedPTWU);
        result.setDiffsetTids(toIntArray(newDiffsetList));

        return result;
    }

    // =========================================================================
    // Fallback path: sorted-merge with diffset construction
    // =========================================================================

    /**
     * Standard two-pointer sorted-merge join — identical to {@link SortedMergeJoin} in
     * correctness — but additionally computes and attaches the diffset of A∪B
     * relative to A (the prefix ul1), enabling subsequent recursive calls to use
     * the fast {@link #joinViaDiffsets} path.
     *
     * <h4>Diffset construction during merge</h4>
     * <pre>
     *   DiffSet(A∪B, A) = TidSet(A) \ TidSet(A∪B)
     *                   = TIDs in ul1 that are NOT matched by ul2
     * </pre>
     * These are naturally collected during the merge: any TID in ul1 that either
     * (a) has no corresponding TID in ul2, or (b) survives the match but has
     * negligible log-probability (treated as absent), contributes to the diffset.
     *
     * @param ul1        prefix utility list A (TID-sorted ascending)
     * @param ul2        extension utility list B (TID-sorted ascending)
     * @param joinedPTWU pre-computed {@code min(PTWU(A), PTWU(B))}
     * @return joined PULList for A∪B with diffset attached, or {@code null} if empty
     */
    private PULList joinViaSortedMerge(IUtilityList ul1, IUtilityList ul2, double joinedPTWU) {

        int size1 = ul1.getSize();
        int size2 = ul2.getSize();

        List<PULList.Element> elements  = new ArrayList<>(Math.min(size1, size2));
        List<Integer>         diffsetList = new ArrayList<>(); // DiffSet(A∪B, A)

        int i = 0, j = 0;
        while (i < size1 && j < size2) {
            int tid1 = ul1.getTid(i);
            int tid2 = ul2.getTid(j);

            if (tid1 == tid2) {
                // Common TID → combine all three fields
                double newUtility   = ul1.getUtility(i)      + ul2.getUtility(j);
                double newRemaining = Math.min(ul1.getRemaining(i), ul2.getRemaining(j));
                double newLogProb   = ul1.getLogProbability(i) + ul2.getLogProbability(j);

                if (newLogProb > AlgorithmConfig.LOG_EPSILON) {
                    // Valid entry: TID is in TidSet(A∪B)
                    elements.add(new PULList.Element(tid1, newUtility, newRemaining, newLogProb));
                } else {
                    // Negligible probability: treat TID as absent from A∪B → diffset
                    diffsetList.add(tid1);
                }
                i++;
                j++;

            } else if (tid1 < tid2) {
                // tid1 ∈ TidSet(A) but ∉ TidSet(B) → not in TidSet(A∪B) → diffset
                diffsetList.add(tid1);
                i++;
            } else {
                // tid2 ∈ TidSet(B) but ∉ TidSet(A) → cannot appear in A∪B (A∪B ⊆ A)
                j++;
            }
        }

        // Any remaining TIDs in A with no B counterpart → diffset
        while (i < size1) {
            diffsetList.add(ul1.getTid(i));
            i++;
        }
        // Remaining TIDs in B (j < size2) are simply ignored — not reachable from A

        if (elements.isEmpty()) {
            return null;
        }

        // Build result and attach diffset for subsequent recursive calls
        Set<Integer> newItemset = new HashSet<>(ul1.getItemset());
        newItemset.addAll(ul2.getItemset());

        PULList result = new PULList(newItemset, elements, joinedPTWU);
        result.setDiffsetTids(toIntArray(diffsetList));

        return result;
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    /**
     * Converts a primitive {@code int[]} to a {@link Set}{@code <Integer>} for O(1) lookup.
     *
     * @param arr source array (may be {@code null} or empty)
     * @return mutable {@link HashSet} (never {@code null})
     */
    private static Set<Integer> toIntSet(int[] arr) {
        if (arr == null || arr.length == 0) return new HashSet<>(0);
        Set<Integer> set = new HashSet<>(arr.length * 2);
        for (int v : arr) set.add(v);
        return set;
    }

    /**
     * Converts a {@link List}{@code <Integer>} to a primitive {@code int[]}.
     *
     * @param list source list (not {@code null}, may be empty)
     * @return primitive {@code int[]} of the same size
     */
    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int k = 0; k < arr.length; k++) arr[k] = list.get(k);
        return arr;
    }
}