package ootkhuimunpnu.vntm.join;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.utility.PULList;

import java.util.*;

/**
 * ULB-Miner–inspired Buffered Join Strategy for Uncertain Databases.
 *
 * <h2>Motivation (from ULB-Miner paper)</h2>
 * <p>Standard join strategies (e.g., {@link SortedMergeJoin}) allocate one new
 * {@code PULList} object per candidate itemset. When tens of millions of
 * candidates are explored, this causes:
 * <ol>
 *   <li>Enormous GC pressure from short-lived objects.</li>
 *   <li>Cache thrashing because each {@code PULList} lands in a different
 *       heap region.</li>
 *   <li>Wasted work: if the itemset is pruned immediately after the join,
 *       the allocated memory is freed without being reused.</li>
 * </ol>
 *
 * <h2>Solution — the ULB Buffer</h2>
 * <p>This class maintains three large, <em>pre-allocated</em> global arrays:
 * <pre>
 *   int[]    globalTids   — transaction IDs of result elements
 *   double[] globalUtils  — u(X,T) = EU contribution numerator
 *   double[] globalRems   — rtu(X,T) = remaining positive utility
 *   double[] globalLogP   — log P(X,T) = sum of item log-probabilities
 * </pre>
 * A pair of integer "watermarks" ({@code writeStart}, {@code writeEnd})
 * tracks the region written by the most recent join.  The arrays are never
 * freed; instead, if the join result is pruned, {@code writeEnd} is simply
 * reset to {@code writeStart}, overwriting those slots in the next join
 * (Algorithm 6, Definition 9 of the ULB-Miner paper).
 *
 * <h2>Probabilistic Extension</h2>
 * <p>The original ULB-Miner paper stores only {@code Iutils} and {@code Rutils}.
 * Because our database is <em>uncertain</em>, we add a third parallel array
 * {@code globalLogP} for {@code log P(X,T)}.  The semantics follow the same
 * attribute-level independence model used in {@link SortedMergeJoin}:
 * <pre>
 *   log P(A∪B, T) = log P(A,T) + log P(B,T)
 * </pre>
 *
 * <h2>Memory Reuse (Algorithm 6 equivalent)</h2>
 * <p>After each join, the caller decides whether the itemset is promising.
 * If it is pruned, it calls {@link #rollback()} to reset {@code writeEnd}
 * to {@code writeStart}, making those slots available for the next join.
 * Only when the result is accepted (used for further expansion or added to
 * top-K) does the caller call {@link #commit()}, which advances
 * {@code writeStart} to {@code writeEnd} permanently.
 *
 * <h2>Buffer View in PULList</h2>
 * <p>Because the actual data lives in this class's arrays, the returned
 * {@link PULList} is constructed from the committed slice.  The standard
 * {@code PULList} constructor copies data into its own arrays; to avoid
 * that copy when the PULList is just a "view", see the note in
 * {@link #join(IUtilityList, IUtilityList, double)} — we build from the
 * already-written slice using {@link PULList.Element} objects (one-shot
 * construction as in the baseline), but we do so only <em>after</em>
 * deciding the itemset is not pruned by PTWU.  This amortises the cost
 * compared to the baseline by avoiding allocations for pruned candidates.
 *
 * <h2>Thread Safety</h2>
 * <p><b>NOT thread-safe.</b> The global buffer is shared state. In a
 * parallel search each thread should own its own {@code BufferedJoin}
 * instance with its own buffer.
 *
 * <h2>Usage in ExperimentRunner</h2>
 * <pre>
 *   // Replace: IJoinStrategy joinStrategy = new PULListJoin();
 *   IJoinStrategy joinStrategy = new BufferedJoin();
 *   DepthFirstSearch dfs = new DepthFirstSearch(joinStrategy);
 * </pre>
 *
 * - ULB-Miner adaptation + uncertain-data extension
 * 
 * @author Meg
 * @version 5.0
 * 
 * @see IJoinStrategy     strategy interface
 * @see SortedMergeJoin   baseline join (allocates a new PULList per candidate)
 * @see DiffsetJoin       alternative diffset-based join (better for dense data)
 */
public class BufferedJoin implements IJoinStrategy {

    // =========================================================================
    // Buffer configuration
    // =========================================================================

    /**
     * Default initial capacity of each buffer array (number of TID slots).
     * Sized for ~1 M transactions; auto-grows if exceeded.
     */
    private static final int DEFAULT_BUFFER_CAPACITY = 1 << 20; // 1 048 576

    /**
     * Growth factor when the buffer must be expanded.
     * The new capacity = old × GROWTH_FACTOR.
     */
    private static final double GROWTH_FACTOR = 1.5;

    // =========================================================================
    // Global buffer arrays  (Definition 9 in the ULB-Miner paper)
    // =========================================================================

    /** Parallel array: transaction IDs of matching elements. */
    private int[]    globalTids;

    /**
     * Parallel array: u(X,T) — internal (direct) utility of itemset X in T.
     * Maps to {@code Iutils} in the paper.
     */
    private double[] globalUtils;

    /**
     * Parallel array: rtu(X,T) — remaining positive utility in T after X.
     * Maps to {@code Rutils} in the paper.
     */
    private double[] globalRems;

    /**
     * Parallel array: log P(X,T) — log of joint existential probability.
     * <b>Extension</b>: not present in the original ULB-Miner paper.
     * Required for the uncertain-data model used in this project.
     */
    private double[] globalLogP;

    // =========================================================================
    // Watermarks — implement the "reusing-memory" mechanism (Algorithm 6)
    // =========================================================================

    /**
     * Index in the global buffer where the <em>current</em> join starts
     * writing.  If the join result is later rolled back, the next join
     * simply writes from this position again, overwriting the discarded data.
     */
    private int writeStart;

    /**
     * Index one past the last element written by the current join.
     * {@code writeEnd - writeStart} == number of result elements so far.
     */
    private int writeEnd;

    // =========================================================================
    // Diagnostic counters
    // =========================================================================

    /** Number of joins that were committed (not rolled back). */
    private long committedJoins;

    /** Number of joins that were rolled back (pruned before PULList creation). */
    private long rolledBackJoins;

    /** Peak buffer occupancy seen so far (in number of TID slots). */
    private int peakOccupancy;

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Creates a {@code BufferedJoin} with the default buffer capacity
     * ({@value #DEFAULT_BUFFER_CAPACITY} slots per array).
     */
    public BufferedJoin() {
        this(DEFAULT_BUFFER_CAPACITY);
    }

    /**
     * Creates a {@code BufferedJoin} with an explicit initial capacity.
     *
     * @param initialCapacity number of TID slots to pre-allocate
     */
    public BufferedJoin(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException(
                "initialCapacity must be positive, got: " + initialCapacity);
        }
        allocateBuffers(initialCapacity);
        this.writeStart = 0;
        this.writeEnd   = 0;
    }

    // =========================================================================
    // IJoinStrategy — core join
    // =========================================================================

    /**
     * Performs a buffered sorted-merge join of {@code ul1} and {@code ul2}.
     *
     * <h3>Steps</h3>
     * <ol>
     *   <li><b>PTWU guard</b> — if min(PTWU_A, PTWU_B) &lt; threshold,
     *       return {@code null} immediately (no buffer write at all).</li>
     *   <li><b>Set write cursor</b> — record {@code writeStart = writeEnd}.</li>
     *   <li><b>Linear-time merge</b> — two-pointer scan of TID arrays;
     *       for each common TID write one slot into the global buffer.</li>
     *   <li><b>Empty-result guard</b> — if no common TIDs, rollback and
     *       return {@code null}.</li>
     *   <li><b>Materialise PULList</b> — read the slice [{@code writeStart},
     *       {@code writeEnd}) from the buffer and construct a {@link PULList}.
     *       Commit the slice (advance {@code writeStart}).</li>
     * </ol>
     *
     * <p><b>Memory reuse note</b>: Because {@link PULList}'s constructor
     * copies data into its own internal arrays, the buffer slice is consumed
     * immediately after the {@code PULList} is built. We therefore call
     * {@link #commit()} inside this method (the "consumed" case). If future
     * refactoring introduces a zero-copy "view" PULList, the commit/rollback
     * responsibility can be moved to the caller.
     *
     * {@inheritDoc}
     */
    @Override
    public PULList join(IUtilityList ul1, IUtilityList ul2, double threshold) {

        // -----------------------------------------------------------------
        // Guard 1 — PTWU upper-bound pruning (no buffer write needed)
        // PTWU(A∪B) = min(PTWU(A), PTWU(B))
        // EU(A∪B) ≤ PTWU(A∪B) → prune if below threshold
        // -----------------------------------------------------------------
        double joinedPTWU = Math.min(ul1.getPtwu(), ul2.getPtwu());
        if (joinedPTWU < threshold - AlgorithmConfig.EPSILON) {
            rolledBackJoins++;
            return null;
        }

        int size1 = ul1.getSize();
        int size2 = ul2.getSize();
        if (size1 == 0 || size2 == 0) {
            rolledBackJoins++;
            return null;
        }

        // -----------------------------------------------------------------
        // Set write cursor — mark the start of this join's region
        // (Algorithm 6, ULBReusingMemory-Construct)
        // -----------------------------------------------------------------
        int joinStart = writeEnd; // remember where this join begins

        // -----------------------------------------------------------------
        // Ensure buffer can hold at most min(size1, size2) new elements
        // -----------------------------------------------------------------
        ensureCapacity(writeEnd + Math.min(size1, size2));

        // -----------------------------------------------------------------
        // Two-pointer sorted-merge (linear time, O(|ul1| + |ul2|))
        // For each common TID, write one slot into the global buffer.
        // -----------------------------------------------------------------
        int i = 0, j = 0;
        while (i < size1 && j < size2) {
            int tid1 = ul1.getTid(i);
            int tid2 = ul2.getTid(j);

            if (tid1 == tid2) {
                // --------------------------------------------------------
                // Common transaction T — combine all three fields:
                //   u(A∪B,T)   = u(A,T)   + u(B,T)
                //   rtu(A∪B,T) = min(rtu(A,T), rtu(B,T))  [conservative]
                //   logP(A∪B,T)= logP(A,T) + logP(B,T)    [independence]
                // --------------------------------------------------------
                double newLogP = ul1.getLogProbability(i) + ul2.getLogProbability(j);

                // Skip negligibly probable transactions (numerical stability)
                if (newLogP > AlgorithmConfig.LOG_EPSILON) {
                    // Grow buffer on-the-fly if this transaction pushes past capacity
                    if (writeEnd >= globalTids.length) {
                        ensureCapacity(writeEnd + 1);
                    }

                    globalTids [writeEnd] = tid1;
                    globalUtils[writeEnd] = ul1.getUtility(i)   + ul2.getUtility(j);
                    globalRems [writeEnd] = Math.min(ul1.getRemaining(i), ul2.getRemaining(j));
                    globalLogP [writeEnd] = newLogP;
                    writeEnd++;
                }
                i++;
                j++;

            } else if (tid1 < tid2) {
                i++;  // T not in ul2
            } else {
                j++;  // T not in ul1
            }
        }

        // -----------------------------------------------------------------
        // Empty result — roll back buffer, no allocation
        // -----------------------------------------------------------------
        int resultSize = writeEnd - joinStart;
        if (resultSize == 0) {
            writeEnd = joinStart; // roll back (overwrite next time)
            rolledBackJoins++;
            return null;
        }

        // -----------------------------------------------------------------
        // Materialise PULList from the buffer slice [joinStart, writeEnd)
        // PULList copies into its own arrays → buffer slice is consumed.
        // -----------------------------------------------------------------
        List<PULList.Element> elements = new ArrayList<>(resultSize);
        for (int k = joinStart; k < writeEnd; k++) {
            elements.add(new PULList.Element(
                    globalTids[k],
                    globalUtils[k],
                    globalRems[k],
                    globalLogP[k]));
        }

        // Build itemset X = A ∪ B
        Set<Integer> newItemset = new HashSet<>(ul1.getItemset());
        newItemset.addAll(ul2.getItemset());

        PULList result = new PULList(newItemset, elements, joinedPTWU);

        // Commit: the PULList owns its data; buffer slot is now free to reuse
        commit(joinStart);

        // Track peak occupancy
        if (writeEnd > peakOccupancy) {
            peakOccupancy = writeEnd;
        }

        return result;
    }

    // =========================================================================
    // Buffer lifecycle helpers
    // =========================================================================

    /**
     * Commits the current join: advances {@code writeStart} back to
     * {@code writeEnd}.  Because {@link PULList} copies the data in its
     * constructor, the buffer slice is immediately reusable.
     *
     * @param joinStart the value of {@code writeEnd} before the join started,
     *                  used to reset the watermark after the PULList is built
     */
    private void commit(int joinStart) {
        // Since PULList has already copied the data, we can safely reset writeEnd
        // to joinStart, making the entire slice reusable for the next join.
        writeEnd = joinStart;
        committedJoins++;
    }

    /**
     * Rolls back the most recent join, discarding its buffer writes.
     * The next join will overwrite the same slots.
     *
     * <p>This method is provided for callers that want explicit lifecycle
     * control (e.g., a future zero-copy PULList variant).
     */
    public void rollback() {
        writeEnd = writeStart;
        rolledBackJoins++;
    }

    /**
     * Resets the entire buffer, discarding all committed and pending data.
     * Useful when starting a fresh mining run without recreating the object.
     */
    public void resetBuffer() {
        writeStart = 0;
        writeEnd   = 0;
    }

    // =========================================================================
    // Buffer growth
    // =========================================================================

    /**
     * Ensures that the global buffer can hold at least {@code requiredCapacity}
     * elements without reallocation.  If the current capacity is sufficient,
     * this is a no-op.
     *
     * @param requiredCapacity minimum number of slots needed
     */
    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= globalTids.length) return;

        int newCapacity = Math.max(
            requiredCapacity,
            (int) (globalTids.length * GROWTH_FACTOR));

        globalTids  = Arrays.copyOf(globalTids,  newCapacity);
        globalUtils = Arrays.copyOf(globalUtils, newCapacity);
        globalRems  = Arrays.copyOf(globalRems,  newCapacity);
        globalLogP  = Arrays.copyOf(globalLogP,  newCapacity);
    }

    /**
     * Allocates (or re-allocates) all four buffer arrays to the given capacity.
     *
     * @param capacity number of TID slots to allocate
     */
    private void allocateBuffers(int capacity) {
        globalTids  = new int   [capacity];
        globalUtils = new double[capacity];
        globalRems  = new double[capacity];
        globalLogP  = new double[capacity];
    }

    // =========================================================================
    // Diagnostics & monitoring
    // =========================================================================

    /**
     * Returns the number of joins whose results were committed (not discarded).
     *
     * @return committed join count
     */
    public long getCommittedJoins() { return committedJoins; }

    /**
     * Returns the number of joins whose results were rolled back (PTWU pruned
     * or empty intersection).
     *
     * @return rolled-back join count
     */
    public long getRolledBackJoins() { return rolledBackJoins; }

    /**
     * Returns the peak number of TID slots ever simultaneously occupied in
     * the global buffer.
     *
     * @return peak occupancy in slots
     */
    public int getPeakOccupancy() { return peakOccupancy; }

    /**
     * Returns the current capacity (length) of the global buffer arrays.
     *
     * @return current buffer capacity in slots
     */
    public int getBufferCapacity() { return globalTids.length; }

    /**
     * Returns the current number of live (committed) TID slots in the buffer.
     * Under the "copy-on-PULList-construction" model this is always 0 between
     * joins.
     *
     * @return live slot count
     */
    public int getLiveSlots() { return writeEnd; }

    /**
     * Returns a human-readable summary of buffer statistics.
     *
     * @return diagnostic string
     */
    public String bufferStats() {
        return String.format(
            "BufferedJoin[capacity=%d, committed=%d, rolledBack=%d, peakOccupancy=%d]",
            globalTids.length, committedJoins, rolledBackJoins, peakOccupancy);
    }

    @Override
    public String toString() {
        return bufferStats();
    }
}