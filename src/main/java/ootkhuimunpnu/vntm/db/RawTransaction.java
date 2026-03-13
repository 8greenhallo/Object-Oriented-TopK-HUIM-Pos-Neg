package ootkhuimunpnu.vntm.db;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a raw uncertain transaction as extracted directly from the input file.
 *
 * <p>In a probabilistic database, each transaction holds items with:
 * <ul>
 *   <li>A <b>quantity</b> (how many units of that item appear)</li>
 *   <li>An <b>existential probability</b> P(item, transaction) ∈ (0, 1] indicating
 *       the likelihood that the item actually appears in this transaction.</li>
 * </ul>
 *
 * <p>This class is a plain data holder (DTO) — it performs no computation.
 * It is consumed by {@link preprocessing.DatabasePreprocessor} to produce
 * optimized {@link Transaction} instances.
 *
 * @author Meg
 * @version 5.0
 */
public class RawTransaction {

    /** Unique transaction identifier (1-based, assigned during file read). */
    private final int tid;

    /**
     * Maps each item ID to its quantity in this transaction.
     * quantity(item, T) ≥ 1
     */
    private final Map<Integer, Integer> items;

    /**
     * Maps each item ID to its existential probability P(item, T) ∈ (0, 1].
     * Items with probability 0 are typically excluded at read time.
     */
    private final Map<Integer, Double> probabilities;

    /**
     * Constructs a RawTransaction.
     *
     * @param tid           Transaction ID (must be positive)
     * @param items         Map of item ID → quantity (must not be null or empty)
     * @param probabilities Map of item ID → probability (must not be null)
     */
    public RawTransaction(int tid,
                          Map<Integer, Integer> items,
                          Map<Integer, Double> probabilities) {
        this.tid = tid;
        // Defensive copies to preserve immutability of the raw record
        this.items = Collections.unmodifiableMap(new HashMap<>(items));
        this.probabilities = Collections.unmodifiableMap(new HashMap<>(probabilities));
    }

    /**
     * Returns the transaction ID.
     *
     * @return transaction ID
     */
    public int getTid() {
        return tid;
    }

    /**
     * Returns an unmodifiable view of the item → quantity map.
     *
     * @return item quantities
     */
    public Map<Integer, Integer> getItems() {
        return items;
    }

    /**
     * Returns an unmodifiable view of the item → probability map.
     *
     * @return item existential probabilities
     */
    public Map<Integer, Double> getProbabilities() {
        return probabilities;
    }

    /**
     * Returns the number of items in this transaction.
     *
     * @return item count
     */
    public int size() {
        return items.size();
    }

    @Override
    public String toString() {
        return String.format("RawTransaction{tid=%d, items=%d}", tid, items.size());
    }
}