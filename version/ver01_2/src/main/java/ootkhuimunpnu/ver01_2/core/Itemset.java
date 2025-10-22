package ootkhuimunpnu.ver01_2.core;

import java.util.*;

/**
 * Represents a high-utility itemset result from uncertain database mining.
 * This class encapsulates an itemset along with its utility and probability measures.
 * 
 * @author Meg
 * @version 1.2
 */
public class Itemset implements Comparable<Itemset> {
    private final Set<Integer> items;
    private final double expectedUtility;
    private final double existentialProbability;
    
    private final int size;
    private final int hashCode;
    
    /**
     * Constructs a new Itemset with the given properties.
     * 
     * @param items The set of item IDs
     * @param expectedUtility The expected utility value
     * @param existentialProbability The existential probability
     */
    public Itemset(Set<Integer> items, double expectedUtility, double existentialProbability) {
        this.items = Collections.unmodifiableSet(new TreeSet<>(items)); // Use TreeSet for consistent ordering
        this.expectedUtility = expectedUtility;
        this.existentialProbability = existentialProbability;
        this.size = items.size();
        this.hashCode = computeHashCode();
    }
    
    /**
     * Gets the set of items in this itemset.
     * 
     * @return Unmodifiable set of item IDs
     */
    public Set<Integer> getItems() {
        return items;
    }
    
    /**
     * Gets the expected utility of this itemset.
     * 
     * @return Expected utility value
     */
    public double getExpectedUtility() {
        return expectedUtility;
    }
    
    /**
     * Gets the existential probability of this itemset.
     * 
     * @return Existential probability value
     */
    public double getExistentialProbability() {
        return existentialProbability;
    }
    
    /**
     * Gets the size (number of items) of this itemset.
     * 
     * @return Size of the itemset
     */
    public int size() {
        return size;
    }
    
    /**
     * Checks if this itemset contains a specific item.
     * 
     * @param item The item ID to check
     * @return true if the item is present, false otherwise
     */
    public boolean contains(int item) {
        return items.contains(item);
    }
    
    /**
     * Checks if this itemset contains all items from another itemset.
     * 
     * @param other The other itemset
     * @return true if this itemset contains all items from other, false otherwise
     */
    public boolean containsAll(Itemset other) {
        return items.containsAll(other.items);
    }
    
    /**
     * Checks if this itemset is a subset of another itemset.
     * 
     * @param other The other itemset
     * @return true if this itemset is a subset of other, false otherwise
     */
    public boolean isSubsetOf(Itemset other) {
        return other.items.containsAll(this.items);
    }
    
    /**
     * Checks if this itemset is a proper subset of another itemset.
     * 
     * @param other The other itemset
     * @return true if this itemset is a proper subset of other, false otherwise
     */
    public boolean isProperSubsetOf(Itemset other) {
        return this.size < other.size && other.items.containsAll(this.items);
    }
    
    /**
     * Creates a new itemset by adding an item to this itemset.
     * 
     * @param item The item to add
     * @return New itemset containing all items from this itemset plus the new item
     */
    public Set<Integer> union(int item) {
        Set<Integer> newItems = new HashSet<>(items);
        newItems.add(item);
        return newItems;
    }
    
    /**
     * Creates a new itemset by combining this itemset with another.
     * 
     * @param other The other itemset
     * @return New set containing all items from both itemsets
     */
    public Set<Integer> union(Itemset other) {
        Set<Integer> newItems = new HashSet<>(items);
        newItems.addAll(other.items);
        return newItems;
    }
    
    /**
     * Gets the intersection of this itemset with another.
     * 
     * @param other The other itemset
     * @return Set containing items present in both itemsets
     */
    public Set<Integer> intersection(Itemset other) {
        Set<Integer> result = new HashSet<>(items);
        result.retainAll(other.items);
        return result;
    }
    
    /**
     * Calculates the Jaccard similarity with another itemset.
     * 
     * @param other The other itemset
     * @return Jaccard similarity coefficient (0.0 to 1.0)
     */
    public double jaccardSimilarity(Itemset other) {
        Set<Integer> intersection = intersection(other);
        Set<Integer> union = union(other);
        
        if (union.isEmpty()) {
            return 1.0; // Both itemsets are empty
        }
        
        return (double) intersection.size() / union.size();
    }
    
    /**
     * Compares itemsets primarily by expected utility (descending),
     * then by existential probability (descending), then by size (ascending).
     * This provides a natural ordering for top-k mining.
     * 
     * @param other The other itemset to compare
     * @return Comparison result
     */
    @Override
    public int compareTo(Itemset other) {
        // Primary: Expected utility (descending)
        int utilityCompare = Double.compare(other.expectedUtility, this.expectedUtility);
        if (utilityCompare != 0) {
            return utilityCompare;
        }
        
        // Secondary: Existential probability (descending)
        int probabilityCompare = Double.compare(other.existentialProbability, this.existentialProbability);
        if (probabilityCompare != 0) {
            return probabilityCompare;
        }
        
        // Tertiary: Size (ascending - prefer smaller itemsets)
        int sizeCompare = Integer.compare(this.size, other.size);
        if (sizeCompare != 0) {
            return sizeCompare;
        }
        
        // Quaternary: Lexicographic order of items for consistency
        return this.items.toString().compareTo(other.items.toString());
    }
    
    /**
     * Computes hash code for this itemset.
     * 
     * @return Hash code
     */
    private int computeHashCode() {
        return Objects.hash(items, Double.doubleToLongBits(expectedUtility), 
                           Double.doubleToLongBits(existentialProbability));
    }
    
    /**
     * Checks equality with another object.
     * Two itemsets are equal if they contain the same items and have
     * the same utility and probability values.
     * 
     * @param obj The object to compare
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Itemset other = (Itemset) obj;
        return Double.compare(other.expectedUtility, expectedUtility) == 0 &&
               Double.compare(other.existentialProbability, existentialProbability) == 0 &&
               Objects.equals(items, other.items);
    }
    
    /**
     * Returns the pre-computed hash code.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return hashCode;
    }
    
    /**
     * Creates a detailed string representation of this itemset.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return String.format("%s: EU=%.4f, EP=%.4f", 
                           items, expectedUtility, existentialProbability);
    }
    
    /**
     * Creates a compact string representation suitable for file output.
     * 
     * @return Compact string representation
     */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        
        // Add items in sorted order
        boolean first = true;
        for (Integer item : items) {
            if (!first) sb.append(" ");
            sb.append(item);
            first = false;
        }
        
        sb.append(String.format(" #EU: %.4f #EP: %.4f", expectedUtility, existentialProbability));
        return sb.toString();
    }
    
    /**
     * Creates an itemset from a utility-list.
     * 
     * @param utilityList The utility-list to convert
     * @return New itemset with properties from the utility-list
     */
    public static Itemset fromUtilityList(UtilityList utilityList) {
        return new Itemset(
            utilityList.getItemset(),
            utilityList.getSumExpectedUtility(),
            utilityList.getExistentialProbability()
        );
    }
    
    /**
     * Builder pattern for creating itemsets with validation.
     */
    public static class Builder {
        private Set<Integer> items = new HashSet<>();
        private double expectedUtility = 0.0;
        private double existentialProbability = 0.0;
        
        public Builder addItem(int item) {
            items.add(item);
            return this;
        }
        
        public Builder addItems(Collection<Integer> items) {
            this.items.addAll(items);
            return this;
        }
        
        public Builder setExpectedUtility(double expectedUtility) {
            if (expectedUtility < 0) {
                throw new IllegalArgumentException("Expected utility cannot be negative");
            }
            this.expectedUtility = expectedUtility;
            return this;
        }
        
        public Builder setExistentialProbability(double existentialProbability) {
            if (existentialProbability < 0.0 || existentialProbability > 1.0) {
                throw new IllegalArgumentException("Existential probability must be between 0.0 and 1.0");
            }
            this.existentialProbability = existentialProbability;
            return this;
        }
        
        public Itemset build() {
            if (items.isEmpty()) {
                throw new IllegalStateException("Itemset cannot be empty");
            }
            return new Itemset(items, expectedUtility, existentialProbability);
        }
    }
}