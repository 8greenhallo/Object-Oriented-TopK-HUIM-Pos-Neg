package ootkhuimunpnu.vntm.utility;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;

/**
 * Manages the global item-profit mapping loaded from the profit table file.
 *
 * <p>Provides typed access with safe defaults (0.0 for unknown items) and
 * convenience predicates for positive/negative profit classification.
 *
 * @author Meg
 * @version 5.0
 */
public class ProfitTable {

    /** Unmodifiable map from item ID to profit value. */
    private final Map<Integer, Double> table;

    /**
     * Constructs a ProfitTable from a raw map.
     *
     * @param profits item ID → profit value (may include negative values)
     */
    public ProfitTable(Map<Integer, Double> profits) {
        this.table = Collections.unmodifiableMap(new HashMap<>(profits));
    }

    /**
     * Returns the profit for the given item, or 0.0 if not found.
     *
     * @param itemId item identifier
     * @return profit value
     */
    public double getProfit(int itemId) {
        return table.getOrDefault(itemId, 0.0);
    }

    /**
     * Returns {@code true} if the item has strictly positive profit.
     *
     * @param itemId item identifier
     * @return whether profit &gt; EPSILON
     */
    public boolean hasPositiveProfit(int itemId) {
        Double p = table.get(itemId);
        return p != null && p > AlgorithmConfig.EPSILON;
    }

    /**
     * Returns the set of all known item IDs.
     *
     * @return unmodifiable set of item IDs
     */
    public Set<Integer> getItemIds() {
        return table.keySet();
    }

    /**
     * Returns the number of items in the profit table.
     *
     * @return item count
     */
    public int size() {
        return table.size();
    }

    /**
     * Returns the underlying unmodifiable profit map.
     *
     * @return profit map
     */
    public Map<Integer, Double> asMap() {
        return table;
    }

    @Override
    public String toString() {
        return String.format("ProfitTable{items=%d}", table.size());
    }
}