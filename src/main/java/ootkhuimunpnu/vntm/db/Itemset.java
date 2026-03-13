package ootkhuimunpnu.vntm.db;

import java.util.*;

/**
 * Immutable result representing a discovered high-utility probabilistic itemset.
 *
 * <p>An itemset X is characterised by two key metrics derived from the uncertain database:
 *
 * <ol>
 *   <li><b>Expected Utility (EU)</b>:
 *     <pre>
 *       EU(X) = Σ_{T ∈ D, X ⊆ T}  u(X, T) · P(X, T)
 *     </pre>
 *     where u(X,T) = Σ_{i∈X} profit(i)·qty(i,T)  and
 *     P(X,T) = Π_{i∈X} P(i,T) is the joint probability of X in T
 *     (under the attribute-level uncertainty model).
 *   </li>
 *
 *   <li><b>Existential Probability (EP)</b>:
 *     <pre>
 *       EP(X) = 1 - Π_{T ∈ D, X ⊆ T} (1 - P(X, T))
 *     </pre>
 *     EP captures the probability that X appears in <em>at least one</em>
 *     transaction.  It is computed in log-space to avoid underflow.
 *   </li>
 * </ol>
 *
 * <p>An itemset qualifies for the top-K result if:
 * <pre>
 *   EU(X) ≥ minThreshold  AND  EP(X) ≥ minProbability
 * </pre>
 *
 * <p>Ordering: itemsets are compared in <em>descending</em> expected-utility order
 * so that {@link java.util.TreeSet}/{@link java.util.PriorityQueue} naturally
 * maintain the highest-utility items first.
 *
 * @author Meg
 * @version 5.0
 */
public class Itemset implements Comparable<Itemset> {

    /** The set of item IDs that form this itemset. */
    private final Set<Integer> items;

    /**
     * Expected utility EU(X) as defined above.
     * EU is the primary ranking metric for top-K selection.
     */
    private final double expectedUtility;

    /**
     * Existential probability EP(X) ∈ [0, 1].
     * An itemset must satisfy EP(X) ≥ minProbability to be reported.
     */
    private final double existentialProbability;

    /**
     * Constructs an Itemset result.
     *
     * @param items                  set of item IDs
     * @param expectedUtility        EU(X) — expected utility of the itemset
     * @param existentialProbability EP(X) — existential probability of the itemset
     */
    public Itemset(Set<Integer> items, double expectedUtility, double existentialProbability) {
        this.items = Collections.unmodifiableSet(new HashSet<>(items));
        this.expectedUtility = expectedUtility;
        this.existentialProbability = existentialProbability;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return unmodifiable set of item IDs */
    public Set<Integer> getItems() { return items; }

    /** @return EU(X) — expected utility */
    public double getExpectedUtility() { return expectedUtility; }

    /** @return EP(X) — existential probability */
    public double getExistentialProbability() { return existentialProbability; }

    /** @return number of items (|X|) */
    public int size() { return items.size(); }

    /** @return {@code true} if itemset is empty */
    public boolean isEmpty() { return items.isEmpty(); }

    /** @return {@code true} if itemset contains the given item */
    public boolean contains(int item) { return items.contains(item); }

    // -------------------------------------------------------------------------
    // Ordering: 
    // Descending EU so TreeSet keeps best items at head
    // -------------------------------------------------------------------------

    /**
     * Compares by <em>descending</em> expected utility.
     * Tie-breaking by item set hash ensures consistency with equals.
     *
     * @param other the other itemset
     * @return negative if {@code this} has higher EU, positive if lower
     */
    @Override
    public int compareTo(Itemset other) {
        // Descending EU order
        int cmp = Double.compare(other.expectedUtility, this.expectedUtility);
        if (cmp != 0) return cmp;
        // Consistent tie-breaking (required for TreeSet correctness)
        return Integer.compare(this.items.hashCode(), other.items.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Itemset)) return false;
        return items.equals(((Itemset) obj).items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Itemset{items=%s, EU=%.4f, EP=%.4f}",
                items, expectedUtility, existentialProbability);
    }

    /**
     * Formats the itemset for ranked output.
     *
     * @param rank 1-based rank number
     * @return formatted output line
     */
    public String toOutputString(int rank) {
        List<Integer> sorted = new ArrayList<>(items);
        Collections.sort(sorted);

        StringBuilder sb = new StringBuilder();
        sb.append(rank).append(". {");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(sorted.get(i));
        }
        sb.append("} EU=").append(String.format("%.4f", expectedUtility));
        sb.append(" EP=").append(String.format("%.4f", existentialProbability));
        return sb.toString();
    }
}