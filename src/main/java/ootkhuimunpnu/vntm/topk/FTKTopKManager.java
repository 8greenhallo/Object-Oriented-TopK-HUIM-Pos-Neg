package ootkhuimunpnu.vntm.topk;

import java.util.*;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.db.Itemset;

/**
 * FTKHUIM-inspired Top-K Manager with fast threshold-raising for probabilistic databases.
 *
 * <h2>Core Idea (from FTKHUIM, Vu et al., IEEE Access 2023)</h2>
 * <p>Standard Top-K managers initialise {@code minUtil = 0} and wait for K
 * qualifying itemsets to accumulate before the threshold rises.  FTKHUIM's key
 * insight is that <em>every transaction is itself an itemset</em>: its
 * Transaction Utility (TU) is a valid upper bound on the utility of any itemset
 * derived from it.  By feeding all TU values into the same global priority queue
 * that stores confirmed HUIs, the threshold jumps to the K-th highest TU
 * <strong>before the depth-first search even begins</strong>.</p>
 *
 * <h2>Probabilistic Adaptation</h2>
 * <p>The original paper operates on deterministic utilities.  In our uncertain
 * database setting:</p>
 * <ul>
 *   <li>All comparisons use {@code Itemset.getExpectedUtility()} (EU) instead
 *       of exact utility.</li>
 *   <li>TU seeds are injected via {@link #seedTransactionUtility(double)} using
 *       the <em>expected</em> transaction utility, i.e. the sum of
 *       (quantity × unit profit × existence probability) over the transaction's
 *       items.</li>
 *   <li>Confirmed HUI entries carry their full existential probability so
 *       callers can apply a secondary EP filter if required.</li>
 * </ul>
 *
 * <h2>Global Priority Queue (RTU strategy)</h2>
 * <p>The paper's central contribution is a <strong>single, persistent
 * min-heap</strong> of size K that is shared across all threshold-raising
 * phases.  Previous algorithms discarded values computed by earlier strategies
 * (RIU, LIU) when switching to the next phase, forcing re-computation.
 * By keeping one heap throughout, every high-utility candidate discovered in
 * any phase immediately contributes to raising {@code minUtil}.</p>
 *
 * <pre>
 * Invariant: heap.peek().expectedUtility == current minUtil
 *            (when heap.size() == K)
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All public mutators are {@code synchronized}.  The seeding phase
 * (Algorithm 1, steps 2–3) typically runs single-threaded; the search phase
 * may be multi-threaded.</p>
 *
 * @author Meg
 * @version 5.0
 * 
 * @see ITopKManager      interface this class implements
 * @see DualTopKManager   alternative TreeSet-based implementation with O(log K) insertion
 * @see TopHUIManager     simpler manager that maintains only confirmed HUIs (no TU seeds)
 */
public class FTKTopKManager implements ITopKManager {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Sentinel EU for "heap slot occupied by a TU seed, not a real HUI". */
    private static final double TU_SEED_EP = -1.0;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Desired number of top-K results. */
    private final int k;

    /**
     * Global min-heap (size ≤ K).  The root always holds the entry with the
     * <em>lowest</em> EU, so {@code heap.peek().eu == minUtil}.
     *
     * <p>Heap entries may be either:
     * <ol>
     *   <li>TU seeds  – injected during the pre-scan phase (RTU strategy).</li>
     *   <li>Confirmed HUIs – inserted by {@link #tryAdd} during search.</li>
     * </ol>
     * TU seeds are evicted as soon as a real HUI with equal or higher EU is
     * found, or when the heap is trimmed.</p>
     */
    private final PriorityQueue<HeapEntry> heap;

    /**
     * HashMap for O(1) duplicate detection during the search phase.
     * Keys: item-set; Values: the corresponding heap entry (if still present).
     *
     * <p>TU seeds are <em>not</em> inserted into this map because they are
     * anonymous (not associated with a specific itemset).</p>
     */
    private final Map<Set<Integer>, HeapEntry> itemsetIndex;

    /** Current minimum EU threshold (= root of heap when full, else 0.0). */
    private volatile double minUtil;

    /**
     * Number of TU seeds currently residing in the heap.
     * Used only for diagnostics / logging.
     */
    private int seedCount = 0;

    // -----------------------------------------------------------------------
    // Inner class: HeapEntry
    // -----------------------------------------------------------------------

    /**
     * Lightweight heap entry that avoids storing heavy {@link Itemset} objects
     * for TU seeds while keeping the heap comparator simple.
     *
     * <p>For <strong>TU seeds</strong>: {@code items == null}, {@code ep == TU_SEED_EP}.</p>
     * <p>For <strong>confirmed HUIs</strong>: all fields populated.</p>
     */
    private static final class HeapEntry implements Comparable<HeapEntry> {
        final Set<Integer> items; // null for TU seeds
        final double eu;
        final double ep;          // TU_SEED_EP for seeds

        HeapEntry(Set<Integer> items, double eu, double ep) {
            this.items = items;
            this.eu    = eu;
            this.ep    = ep;
        }

        /** Min-heap order: smallest EU at root. */
        @Override
        public int compareTo(HeapEntry o) {
            return Double.compare(this.eu, o.eu);
        }

        boolean isSeed() {
            return ep == TU_SEED_EP;
        }

        @Override
        public String toString() {
            return isSeed()
                ? String.format("TuSeed{eu=%.4f}", eu)
                : String.format("HUI{items=%s, eu=%.4f, ep=%.4f}", items, eu, ep);
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs an FTKTopKManager.
     *
     * @param k number of top itemsets to maintain (must be ≥ 1)
     */
    public FTKTopKManager(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1, got: " + k);
        this.k            = k;
        this.heap         = new PriorityQueue<>(k + 1); // +1 avoids resize on over-insertion
        this.itemsetIndex = new HashMap<>();
        this.minUtil      = 0.0;
    }

    /**
     * Constructs an FTKTopKManager using the standard {@link AlgorithmConfig}.
     *
     * @param config algorithm configuration carrying the initial k value
     */
    public FTKTopKManager(AlgorithmConfig config) {
        this(config.getK());
    }

    // -----------------------------------------------------------------------
    // RTU Strategy: Transaction Utility Seeding
    // -----------------------------------------------------------------------

    /**
     * <strong>RTU Strategy (Algorithm 1, step 3)</strong> – injects an
     * expected transaction utility value into the global heap so that
     * {@code minUtil} can be raised before the depth-first search begins.
     *
     * <p>Call this method once per transaction during the initial database scan.
     * After seeding all transactions, {@code getThreshold()} returns the K-th
     * highest TU value — a tight upper bound on the minimum utility of the
     * true top-K HUIs.</p>
     *
     * <p><em>Probabilistic mapping:</em> pass the <em>expected</em> transaction
     * utility, i.e.
     * {@code ETU(t) = Σ_{i∈t} quantity(i,t) × profit(i) × prob(i,t)}.</p>
     *
     * @param expectedTU expected transaction utility (≥ 0)
     */
    public synchronized void seedTransactionUtility(double expectedTU) {
        if (expectedTU <= 0.0) return; // non-positive TU cannot raise threshold

        HeapEntry seed = new HeapEntry(null, expectedTU, TU_SEED_EP);
        insertEntry(seed);

        if (seed == heap.peek()) {
            // This seed became the new minimum — it is already the floor
        }
        // Threshold is updated inside insertEntry
    }

    /**
     * Convenience bulk-seeder.  Equivalent to calling
     * {@link #seedTransactionUtility(double)} for each element.
     *
     * @param transactionUtilities collection of expected TU values
     */
    public synchronized void seedAll(Collection<Double> transactionUtilities) {
        for (double tu : transactionUtilities) {
            seedTransactionUtility(tu);
        }
    }

    // -----------------------------------------------------------------------
    // ITopKManager implementation
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <h3>FTKHUIM insertion logic (ExploreKHUI, line 6)</h3>
     * <ol>
     *   <li>Fast-reject: if heap is full and {@code expectedUtility < minUtil}
     *       (within ε), the itemset cannot displace any current member.</li>
     *   <li>Duplicate check: if the itemset is already indexed, update it only
     *       if the new EU is strictly better.</li>
     *   <li>Insert a new {@link HeapEntry}; if heap exceeds K, evict the root
     *       (lowest EU).</li>
     *   <li>Update {@link #minUtil} immediately — this is the "global structure"
     *       trick from the paper that makes subsequent pruning tighter.</li>
     * </ol>
     */
    @Override
    public synchronized boolean tryAdd(Set<Integer> items,
                                        double expectedUtility,
                                        double existentialProbability) {
        // 1. Fast reject
        if (heap.size() >= k && expectedUtility < minUtil - AlgorithmConfig.EPSILON) {
            return false;
        }

        // 2. Duplicate update
        HeapEntry existing = itemsetIndex.get(items);
        // If already present, update only if strictly better EU
        if (existing != null) {
            if (expectedUtility > existing.eu + AlgorithmConfig.EPSILON) {
                // Replace: remove old entry from heap and re-insert
                heap.remove(existing);
                itemsetIndex.remove(items);
                HeapEntry updated = new HeapEntry(items, expectedUtility, existentialProbability);
                insertEntry(updated);
                itemsetIndex.put(items, updated);
                return true;
            }
            return false; // existing is already at least as good
        }

        // 3. New confirmed HUI
        HeapEntry entry = new HeapEntry(items, expectedUtility, existentialProbability);
        insertEntry(entry);
        itemsetIndex.put(items, entry);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the EU of the K-th best entry (the heap root).  Before K
     * entries are accumulated, returns {@code 0.0}.</p>
     */
    @Override
    public synchronized double getThreshold() {
        return minUtil;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns only <em>confirmed HUI</em> entries (TU seeds are excluded)
     * in descending EU order.</p>
     */
    @Override
    public synchronized List<Itemset> getTopK() {
        // Collect confirmed HUI entries, sort descending by EU
        List<HeapEntry> confirmed = new ArrayList<>(heap.size());
        for (HeapEntry e : heap) {
            if (!e.isSeed()) confirmed.add(e);
        }
        confirmed.sort((a, b) -> Double.compare(b.eu, a.eu));

        List<Itemset> result = new ArrayList<>(confirmed.size());
        for (HeapEntry e : confirmed) {
            result.add(new Itemset(e.items, e.eu, e.ep));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reports total heap occupancy (seeds + confirmed HUIs).</p>
     */
    @Override
    public synchronized int size() {
        return heap.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean isFull() {
        return heap.size() >= k;
    }

    // -----------------------------------------------------------------------
    // Additional FTKHUIM-specific API
    // -----------------------------------------------------------------------

    /**
     * Returns the number of confirmed HUI entries currently in the heap
     * (i.e., excluding TU seeds).
     *
     * @return confirmed HUI count
     */
    public synchronized int confirmedSize() {
        int count = 0;
        for (HeapEntry e : heap) {
            if (!e.isSeed()) count++;
        }
        return count;
    }

    /**
     * Returns the number of TU seed entries currently occupying heap slots.
     *
     * @return seed count
     */
    public int getSeedCount() {
        return seedCount;
    }

    /**
     * Attempts to raise the threshold using an externally supplied utility
     * value (e.g., from LIU or CUD intermediate strategies in Algorithm 1,
     * steps 7 and 19–20).  This is equivalent to calling
     * {@link #seedTransactionUtility(double)} but semantically distinct.
     *
     * @param utilityHint the utility value that should be considered for
     *                    raising {@code minUtil}
     */
    public synchronized void raiseThreshold(double utilityHint) {
        seedTransactionUtility(utilityHint);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Inserts {@code entry} into the heap and trims the heap to size K.
     * Updates {@link #minUtil} after every structural change.
     *
     * <p>When the evicted entry is a TU seed, {@link #seedCount} is
     * decremented; when the new entry is a seed, it is incremented.</p>
     */
    private void insertEntry(HeapEntry entry) {
        if (entry.isSeed()) seedCount++;

        heap.offer(entry);

        if (heap.size() > k) {
            // Evict the entry with the lowest EU (the root of the min-heap)
            HeapEntry evicted = heap.poll();
            if (evicted != null) {
                if (evicted.isSeed()) {
                    seedCount--;
                } else {
                    // Remove from index only if it is still the current entry
                    // (it may have been replaced by a duplicate-update)
                    itemsetIndex.remove(evicted.items, evicted);
                }
            }
        }

        refreshMinUtil();
    }

    /**
     * Refreshes {@link #minUtil} to match the heap root (the lowest EU in the
     * current top-K pool).  Called after every heap mutation.
     */
    private void refreshMinUtil() {
        HeapEntry root = heap.peek();
        minUtil = (root != null && heap.size() >= k) ? root.eu : 0.0;
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    @Override
    public synchronized String toString() {
        return String.format(
            "FTKTopKManager{k=%d, heapSize=%d, confirmedHUIs=%d, seeds=%d, minUtil=%.4f}",
            k, heap.size(), confirmedSize(), seedCount, minUtil);
    }
}