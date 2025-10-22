package ootkhuimunpnu.ver02.core;

import java.util.*;

/**
 * Itemset result class representing a high-utility itemset
 * 
 * @author Meg
 * @version 2.0
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
        // Sort items for consistent display
        List<Integer> sortedItems = new ArrayList<>(items);
        Collections.sort(sortedItems);
        
        return sortedItems + ": EU=" + String.format("%.4f", expectedUtility) +
               ", EP=" + String.format("%.4f", existentialProbability);
    }

    /**
     * Create itemset from utility list
     */
    public static Itemset fromUtilityList(UtilityList ul) {
        return new Itemset(ul.getItemset(), ul.getSumEU(), ul.getExistentialProbability());
    }
}