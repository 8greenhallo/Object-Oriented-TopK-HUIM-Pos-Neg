package ootkhuimunpnu.ver03.core;

import java.util.*;

/**
 * Itemset result class representing a high-utility itemset
 * 
 * Design Principles:
 * - Immutability: All fields are final and cannot be modified
 * - Value Object: Equality based on itemset content
 * - Comparable: Natural ordering by expected utility (descending)
 * 
 * MODIFICATION:
 * - Add getSortedItems function for better logging
 * 
 * @author Meg
 * @version 3.0
 */
public class Itemset implements Comparable<Itemset> {
    private final Set<Integer> items;
    private final double expectedUtility;
    private final double existentialProbability;

    public Itemset(Set<Integer> items, double expectedUtility, double existentialProbability) {
        this.items = Collections.unmodifiableSet(new HashSet<>(items));
        this.expectedUtility = expectedUtility;
        this.existentialProbability = existentialProbability;
    }

    public Set<Integer> getItems() {
        return items;
    }

    public double getExpectedUtility() {
        return expectedUtility;
    }

    public double getExistentialProbability() {
        return existentialProbability;
    }

    public int size() {
        return items.size();
    }

    /**
     * Gets a sorted list representation of items for display.
     */
    public List<Integer> getSortedItems() {
        List<Integer> sorted = new ArrayList<>(items);
        Collections.sort(sorted);
        return sorted;
    }

    @Override
    public int compareTo(Itemset other) {
        // Sort by expected utility (EU) in descending order
        int cmp = Double.compare(other.expectedUtility, this.expectedUtility);
        if (cmp != 0) return cmp;
        
        // Tie-break using item hashcode for consistency
        return Integer.compare(this.items.hashCode(), other.items.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Itemset itemset = (Itemset) obj;
        return items.equals(itemset.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s: EU= %.4f, EP= %.4f",
        getSortedItems(), expectedUtility, existentialProbability);
    }

    /**
     * Create itemset from utility list
     */
    public static Itemset fromUtilityList(UtilityList ul) {
        return new Itemset(ul.getItemset(), ul.getSumEU(), ul.getExistentialProbability());
    }

    /**
     * Returns a detailed string with additional information.
     */
    public String toDetailedString() {
        return String.format("Itemset{items=%s, size=%d, expectedUtility=%.4f, " +
                           "existentialProbability=%.4f}",
                           getSortedItems(), size(), expectedUtility, existentialProbability);
    }
}