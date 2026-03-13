package ootkhuimunpnu.vntm.db;

/**
 * Immutable representation of a single item with its associated profit.
 *
 * <p>An item in the HUIM context has:
 * <ul>
 *   <li>A unique integer <em>ID</em> (as in the transaction database)</li>
 *   <li>A <em>profit value</em> which may be positive or negative:
 *       <ul>
 *         <li>Positive profit → item increases utility when purchased</li>
 *         <li>Negative profit → item decreases utility (e.g., cost, penalty)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>The profit is set globally (same for all transactions), stored in a
 * {@link io.ProfitTableReader profit table} and loaded at startup.
 *
 * @author Meg
 * @version 5.0
 */
public class Item {

    /** Unique item identifier matching transaction database item IDs. */
    private final int id;

    /**
     * Profit value for this item.
     * 
     * profit &gt; 0 → positive utility contribution
     * profit &lt; 0 → negative utility contribution
     */
    private final double profit;

    /**
     * Constructs an Item.
     *
     * @param id     item identifier
     * @param profit profit value (may be negative)
     */
    public Item(int id, double profit) {
        this.id = id;
        this.profit = profit;
    }

    /**
     * Returns the item ID.
     *
     * @return item ID
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the profit (unit utility) for this item.
     *
     * <p>The utility of item {@code i} in transaction {@code T} is:
     * <pre>
     *   u(i, T) = profit(i) * quantity(i, T)
     * </pre>
     *
     * @return profit value
     */
    public double getProfit() {
        return profit;
    }

    /**
     * Returns {@code true} if this item has a strictly positive profit.
     *
     * @return whether profit &gt; 0
     */
    public boolean hasPositiveProfit() {
        return profit > 0.0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Item)) return false;
        return id == ((Item) obj).id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("Item{id=%d, profit=%.2f}", id, profit);
    }
}