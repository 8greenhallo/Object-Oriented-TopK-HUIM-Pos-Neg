package ootkhuimunpnu.vntm.topk;

import java.util.*;
import java.util.logging.Logger;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.db.Itemset;
import ootkhuimunpnu.vntm.utility.PULList;

/**
 * Top-K Manager with RUC (Raise Upon Confirmation) threshold raising for
 * probabilistic uncertain databases.
 *
 * <h2>Algorithm Lineage</h2>
 * <p>This manager retains exclusively the <b>RUC</b> strategy from the original
 * TopHUI framework. All pre-search seeding strategies (RIU, RTU, RTWU) have been
 * removed. The threshold is raised solely through node confirmations encountered
 * during the depth-first search.</p>
 *
 * <h2>RUC — Raise Upon Confirmation</h2>
 * <p>During the depth-first search, <em>every visited node</em> — not just those
 * that complete a full HUI — is offered to the heap immediately. Leaf nodes
 * (zero remaining utility) are especially valuable because their EU is exact
 * rather than an upper bound, raising the threshold before any sibling subtree
 * is explored.</p>
 *
 * <h2>Structural Decisions</h2>
 *
 * <h3>1. Persistent global min-heap of size K</h3>
 * <p>A single {@link PriorityQueue} ordered by ascending expected utility holds
 * at most K entries at all times. The root is always the weakest member, so
 * {@code minUtil = heap.peek().eu} in O(1).</p>
 *
 * <h3>2. Leaf-node priority in RUC</h3>
 * <p>Inside {@link #tryUpdateThreshold(PULList)}, the insertion eligibility
 * test is asymmetric: a leaf node (remaining ≈ 0) is <em>always</em> eligible
 * regardless of the current threshold, because its EU is exact and is
 * guaranteed to remain useful. Internal nodes are only inserted when already
 * competitive with the current threshold.</p>
 *
 * <h2>Probabilistic Adaptation</h2>
 * <table border="1" cellpadding="4">
 *   <tr><th>Original TopHUI concept</th><th>This implementation</th></tr>
 *   <tr><td>{@code utility(X)}</td>
 *       <td>{@code PULList.getSumEU()} = Σ_T u(X,T)·P(X,T)</td></tr>
 *   <tr><td>Heap comparison key</td>
 *       <td>{@code Itemset.getExpectedUtility()}</td></tr>
 * </table>
 *
 * <h2>Usage in the Search Strategy</h2>
 * <pre>
 * for (PULList ext : extensions) {
 *
 *     // 1. RUC: offer the node before recursing
 *     topK.tryUpdateThreshold(ext);
 *
 *     // 2. Prune with now-possibly-tighter threshold
 *     if (pruning.shouldPruneByEU(ext.getSumEU(),
 *                                  ext.getSumRemaining(),
 *                                  topK.getThreshold())) continue;
 *
 *     // 3. If EU qualifies, register as confirmed HUI
 *     if (pruning.qualifiesForTopK(ext, topK.getThreshold())) {
 *         topK.tryAdd(ext.getItemset(), ext.getSumEU(),
 *                     ext.getExistentialProbability());
 *     }
 *
 *     // 4. Recurse into child nodes
 *     recurse(ext, ...);
 * }
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All public mutators are {@code synchronized}. DFS search
 * (possibly multi-threaded) shares one monitor.</p>
 *
 * @author Meg
 * @version 5.0
 * 
 * @see ITopKManager     interface this class implements
 * @see FTKTopKManager   alternative manager with fast threshold-raising heuristic
 * @see DualTopKManager  alternative manager with dual data structure for O(log K) insertion and O(1) threshold retrieval
 */
public class TopHUITopKManager implements ITopKManager {

    // -----------------------------------------------------------------------
    // Logging
    // -----------------------------------------------------------------------
    private static final Logger LOG =
        Logger.getLogger(TopHUITopKManager.class.getName());

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    /**
     * Single entry held in the min-heap.
     *
     * <p>All entries are confirmed HUI entries — there are no anonymous seed
     * entries in this RUC-only implementation.</p>
     */
    private static final class HeapEntry implements Comparable<HeapEntry> {

        final Set<Integer> items;
        final double eu;
        final double ep;

        HeapEntry(Set<Integer> items, double eu, double ep) {
            this.items = items;
            this.eu    = eu;
            this.ep    = ep;
        }

        /** Min-heap ordering: smallest EU at root. */
        @Override
        public int compareTo(HeapEntry o) {
            return Double.compare(this.eu, o.eu);
        }

        @Override
        public String toString() {
            return String.format("[HUI items=%s eu=%.4f ep=%.4f]", items, eu, ep);
        }
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final int k;

    /**
     * Persistent global min-heap (≤ K entries).
     * Heap root = entry with lowest EU = current {@code minUtil}.
     */
    private final PriorityQueue<HeapEntry> heap;

    /**
     * O(1) lookup for confirmed HUI entries by itemset.
     */
    private final Map<Set<Integer>, HeapEntry> itemsetIndex;

    /**
     * Current minimum EU threshold.
     * Equals {@code heap.peek().eu} when {@code heap.size() >= k}, else 0.0.
     */
    private volatile double minUtil;

    // ------- Diagnostic counters -------------------------------------------
    private int huisInserted    = 0;
    private int thresholdRaises = 0;
    private int leafNodesFound  = 0;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Creates a TopHUITopKManager.
     *
     * @param k desired number of top itemsets (must be ≥ 1)
     */
    public TopHUITopKManager(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1, got: " + k);
        this.k            = k;
        this.heap         = new PriorityQueue<>(k + 1);
        this.itemsetIndex = new HashMap<>();
        this.minUtil      = 0.0;
    }

    /**
     * Creates a TopHUITopKManager from an {@link AlgorithmConfig}.
     *
     * @param config carries the initial k value
     */
    public TopHUITopKManager(AlgorithmConfig config) {
        this(config.getK());
    }

    // =======================================================================
    // DURING-SEARCH THRESHOLD RAISING  (RUC strategy)
    // =======================================================================

    /**
     * <b>RUC strategy</b> — raises the threshold by registering a candidate
     * node <em>before</em> its subtree is recursed.
     *
     * <p>This is the sole threshold-raising mechanism in this manager.
     * The logic proceeds as follows:</p>
     * <ol>
     *   <li>Detect whether the node is a <em>leaf</em> (remaining utility ≈ 0,
     *       meaning EU is exact and no extension can ever add more utility).</li>
     *   <li>Leaf nodes are inserted unconditionally — their exact EU is precious
     *       even if it currently lies below the threshold, because inserting them
     *       may evict a weaker entry and raise the threshold for subsequent nodes.</li>
     *   <li>Internal nodes are inserted only if their EU is already competitive
     *       (EU ≥ minUtil or heap is not yet full).</li>
     *   <li>If the insertion caused the heap root to change, the threshold
     *       is updated and {@code true} is returned so the caller may
     *       immediately re-evaluate pending candidates.</li>
     * </ol>
     *
     * @param node the PULList of the candidate node currently being evaluated
     * @return {@code true} if the minimum utility threshold was raised
     */
    public synchronized boolean tryUpdateThreshold(PULList node) {
        double eu      = node.getSumEU();
        double ep      = node.getExistentialProbability();
        boolean isLeaf = node.getSumRemaining() < AlgorithmConfig.EPSILON;

        if (isLeaf) {
            leafNodesFound++;
            LOG.fine(() -> String.format(
                "RUC leaf: items=%s eu=%.4f ep=%.4f threshold_before=%.4f",
                node.getItemset(), eu, ep, minUtil));
        }

        // Nothing to register if EU is non-positive
        if (eu <= 0.0) return false;

        // Eligibility:
        //   - leaf nodes  : always eligible (EU is exact)
        //   - non-leaf    : only if competitive or heap has room
        boolean eligible = isLeaf
            || heap.size() < k
            || eu >= minUtil - AlgorithmConfig.EPSILON;

        if (!eligible) return false;

        double       prevMinUtil = minUtil;
        Set<Integer> items       = node.getItemset();

        // Duplicate-update path
        HeapEntry existing = itemsetIndex.get(items);
        if (existing != null) {
            if (eu > existing.eu + AlgorithmConfig.EPSILON) {
                heap.remove(existing);
                itemsetIndex.remove(items);
                HeapEntry updated = new HeapEntry(items, eu, ep);
                offerAndTrim(updated);
                itemsetIndex.put(items, updated);
                huisInserted++;
            }
            // else: existing entry is already as good or better — no-op
        } else {
            HeapEntry entry = new HeapEntry(items, eu, ep);
            offerAndTrim(entry);
            itemsetIndex.put(items, entry);
            huisInserted++;
        }

        refreshMinUtil();
        boolean raised = minUtil > prevMinUtil + AlgorithmConfig.EPSILON;
        if (raised) {
            thresholdRaises++;
            LOG.fine(() -> String.format(
                "Threshold raised %.4f → %.4f (leaf=%b)",
                prevMinUtil, minUtil, isLeaf));
        }
        return raised;
    }

    // =======================================================================
    // ITopKManager — mandatory interface methods
    // =======================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Standard confirmed-HUI insertion used when the search strategy has
     * fully validated EU ≥ threshold AND EP ≥ minProbability.</p>
     *
     * <ol>
     *   <li>Fast-reject: EU &lt; minUtil − ε when heap is full.</li>
     *   <li>Duplicate update: replace existing entry only if EU is strictly
     *       better, to avoid spurious heap churn.</li>
     *   <li>Insert new entry; trim heap to K; refresh {@link #minUtil}.</li>
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

        double prevMinUtil = minUtil;

        // 2. Duplicate update
        HeapEntry existing = itemsetIndex.get(items);
        if (existing != null) {
            if (expectedUtility > existing.eu + AlgorithmConfig.EPSILON) {
                heap.remove(existing);
                itemsetIndex.remove(items);
                HeapEntry updated = new HeapEntry(items, expectedUtility, existentialProbability);
                offerAndTrim(updated);
                itemsetIndex.put(items, updated);
                refreshMinUtil();
                if (minUtil > prevMinUtil + AlgorithmConfig.EPSILON) thresholdRaises++;
                huisInserted++;
                return true;
            }
            return false; // existing is at least as good
        }

        // 3. New confirmed HUI
        HeapEntry entry = new HeapEntry(items, expectedUtility, existentialProbability);
        offerAndTrim(entry);
        itemsetIndex.put(items, entry);
        refreshMinUtil();
        if (minUtil > prevMinUtil + AlgorithmConfig.EPSILON) thresholdRaises++;
        huisInserted++;
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns 0.0 until K entries are accumulated; thereafter the EU of
     * the weakest current top-K member.</p>
     */
    @Override
    public synchronized double getThreshold() {
        return minUtil;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns all confirmed-HUI entries sorted in descending EU order.</p>
     */
    @Override
    public synchronized List<Itemset> getTopK() {
        List<HeapEntry> entries = new ArrayList<>(heap);
        entries.sort((a, b) -> Double.compare(b.eu, a.eu)); // descending

        List<Itemset> result = new ArrayList<>(entries.size());
        for (HeapEntry e : entries) {
            result.add(new Itemset(e.items, e.eu, e.ep));
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized int size() {
        return heap.size();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean isFull() {
        return heap.size() >= k;
    }

    // =======================================================================
    // Diagnostics
    // =======================================================================

    /** Total number of times minUtil moved upward across all operations. */
    public int getThresholdRaiseCount() { return thresholdRaises; }

    /** Total leaf nodes registered via {@link #tryUpdateThreshold}. */
    public int getLeafNodeCount()        { return leafNodesFound;  }

    /** Cumulative HUI insertions via tryAdd + tryUpdateThreshold. */
    public int getHUIsInserted()         { return huisInserted;    }

    // =======================================================================
    // Private helpers
    // =======================================================================

    /**
     * Offers {@code entry} to the heap; if size exceeds K, evicts the root
     * (lowest EU) and removes it from the index.
     */
    private void offerAndTrim(HeapEntry entry) {
        heap.offer(entry);
        if (heap.size() > k) {
            HeapEntry evicted = heap.poll(); // lowest EU leaves
            if (evicted != null) {
                itemsetIndex.remove(evicted.items, evicted);
            }
        }
    }

    /**
     * Refreshes {@link #minUtil} from the current heap state.
     * Must be called after every structural change to the heap.
     */
    private void refreshMinUtil() {
        HeapEntry root = heap.peek();
        minUtil = (root != null && heap.size() >= k) ? root.eu : 0.0;
    }

    // =======================================================================
    // Object overrides
    // =======================================================================

    @Override
    public synchronized String toString() {
        return String.format(
            "TopHUITopKManager{k=%d, heapSize=%d, minUtil=%.4f, raises=%d, leaves=%d, totalHUIs=%d}",
            k, heap.size(), minUtil, thresholdRaises, leafNodesFound, huisInserted);
    }
}