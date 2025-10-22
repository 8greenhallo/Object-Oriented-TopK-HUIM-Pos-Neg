package ootkhuimunpnu.ver01.core;

import java.util.*;

/**
 * Represents a high-utility itemset result with expected utility and existential probability.
 * This is the primary output structure for Top-K High-Utility Itemset Mining.
 * 
 * @author Meg
 * @version 1.0
 */
public class Itemset implements Comparable<Itemset> {
    
    private final Set<Integer> items;
    private final double expectedUtility;
    private final double existentialProbability;
    
    // Optional metadata
    private final long discoveryTimestamp;
    private final int supportCount;

    /**
     * Creates a new itemset result.
     * 
     * @param items Set of item IDs in the itemset
     * @param expectedUtility Expected utility value
     * @param existentialProbability Existential probability value
     */
    public Itemset(Set<Integer> items, double expectedUtility, double existentialProbability) {
        this.items = Collections.unmodifiableSet(new HashSet<>(items));
        this.expectedUtility = expectedUtility;
        this.existentialProbability = existentialProbability;
        this.discoveryTimestamp = System.currentTimeMillis();
        this.supportCount = -1; // Not calculated by default
    }

    /**
     * Creates a new itemset result with support count.
     * 
     * @param items Set of item IDs in the itemset
     * @param expectedUtility Expected utility value
     * @param existentialProbability Existential probability value
     * @param supportCount Number of transactions supporting this itemset
     */
    public Itemset(Set<Integer> items, double expectedUtility, double existentialProbability, int supportCount) {
        this.items = Collections.unmodifiableSet(new HashSet<>(items));
        this.expectedUtility = expectedUtility;
        this.existentialProbability = existentialProbability;
        this.discoveryTimestamp = System.currentTimeMillis();
        this.supportCount = supportCount;
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
     * Gets the discovery timestamp of this itemset.
     * 
     * @return Discovery timestamp in milliseconds
     */
    public long getDiscoveryTimestamp() {
        return discoveryTimestamp;
    }

    /**
     * Gets the support count of this itemset.
     * 
     * @return Support count, or -1 if not calculated
     */
    public int getSupportCount() {
        return supportCount;
    }

    /**
     * Gets the size of this itemset (number of items).
     * 
     * @return Itemset size
     */
    public int size() {
        return items.size();
    }

    /**
     * Checks if this itemset is empty.
     * 
     * @return True if itemset has no items, false otherwise
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Checks if this itemset contains a specific item.
     * 
     * @param itemId Item ID to check
     * @return True if itemset contains the item, false otherwise
     */
    public boolean containsItem(int itemId) {
        return items.contains(itemId);
    }

    /**
     * Checks if this itemset contains all items in another itemset.
     * 
     * @param otherItems Set of items to check
     * @return True if this itemset contains all items, false otherwise
     */
    public boolean containsAll(Set<Integer> otherItems) {
        return items.containsAll(otherItems);
    }

    /**
     * Checks if this itemset is a subset of another itemset.
     * 
     * @param otherItems Set of items to compare against
     * @return True if this itemset is a subset, false otherwise
     */
    public boolean isSubsetOf(Set<Integer> otherItems) {
        return otherItems.containsAll(items);
    }

    /**
     * Checks if this itemset is a superset of another itemset.
     * 
     * @param otherItems Set of items to compare against
     * @return True if this itemset is a superset, false otherwise
     */
    public boolean isSupersetOf(Set<Integer> otherItems) {
        return items.containsAll(otherItems);
    }

    /**
     * Creates the union of this itemset with another set of items.
     * 
     * @param otherItems Set of items to union with
     * @return New itemset containing union of items (utility/probability not computed)
     */
    public Itemset union(Set<Integer> otherItems) {
        Set<Integer> unionItems = new HashSet<>(items);
        unionItems.addAll(otherItems);
        return new Itemset(unionItems, 0.0, 0.0); // Utility/probability need recomputation
    }

    /**
     * Creates the intersection of this itemset with another set of items.
     * 
     * @param otherItems Set of items to intersect with
     * @return New itemset containing intersection of items (utility/probability not computed)
     */
    public Itemset intersection(Set<Integer> otherItems) {
        Set<Integer> intersectionItems = new HashSet<>(items);
        intersectionItems.retainAll(otherItems);
        return new Itemset(intersectionItems, 0.0, 0.0); // Utility/probability need recomputation
    }

    /**
     * Gets the utility-to-size ratio (utility density).
     * 
     * @return Utility per item in the itemset
     */
    public double getUtilityDensity() {
        return items.isEmpty() ? 0.0 : expectedUtility / items.size();
    }

    /**
     * Gets the utility-probability product (combined score).
     * 
     * @return Expected utility multiplied by existential probability
     */
    public double getCombinedScore() {
        return expectedUtility * existentialProbability;
    }

    /**
     * Checks if this itemset meets minimum thresholds.
     * 
     * @param minUtility Minimum expected utility threshold
     * @param minProbability Minimum existential probability threshold
     * @return True if itemset meets both thresholds
     */
    public boolean meetsThresholds(double minUtility, double minProbability) {
        return expectedUtility >= minUtility && existentialProbability >= minProbability;
    }

    /**
     * Compares itemsets by expected utility (descending order).
     * Used for sorting top-K results.
     */
    @Override
    public int compareTo(Itemset other) {
        if (other == null) return 1;
        
        // Primary: Expected utility (descending)
        int utilityComparison = Double.compare(other.expectedUtility, this.expectedUtility);
        if (utilityComparison != 0) {
            return utilityComparison;
        }
        
        // Secondary: Existential probability (descending)
        int probabilityComparison = Double.compare(other.existentialProbability, this.existentialProbability);
        if (probabilityComparison != 0) {
            return probabilityComparison;
        }
        
        // Tertiary: Size (ascending - prefer smaller itemsets)
        int sizeComparison = Integer.compare(this.items.size(), other.items.size());
        if (sizeComparison != 0) {
            return sizeComparison;
        }
        
        // Final: Lexicographic order of items (for consistency)
        return compareItemsLexicographically(this.items, other.items);
    }

    /**
     * Compares two sets of items lexicographically.
     * 
     * @param items1 First set of items
     * @param items2 Second set of items
     * @return Comparison result
     */
    private int compareItemsLexicographically(Set<Integer> items1, Set<Integer> items2) {
        List<Integer> list1 = new ArrayList<>(items1);
        List<Integer> list2 = new ArrayList<>(items2);
        
        Collections.sort(list1);
        Collections.sort(list2);
        
        int minSize = Math.min(list1.size(), list2.size());
        for (int i = 0; i < minSize; i++) {
            int comparison = list1.get(i).compareTo(list2.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        
        return Integer.compare(list1.size(), list2.size());
    }

    /**
     * Creates a comparator for sorting by combined score (utility × probability).
     * 
     * @return Comparator for combined score sorting
     */
    public static Comparator<Itemset> combinedScoreComparator() {
        return (a, b) -> {
            double scoreA = a.getCombinedScore();
            double scoreB = b.getCombinedScore();
            
            int scoreComparison = Double.compare(scoreB, scoreA); // Descending
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            
            return a.compareTo(b); // Fall back to default comparison
        };
    }

    /**
     * Creates a comparator for sorting by utility density.
     * 
     * @return Comparator for utility density sorting
     */
    public static Comparator<Itemset> utilityDensityComparator() {
        return (a, b) -> {
            double densityA = a.getUtilityDensity();
            double densityB = b.getUtilityDensity();
            
            int densityComparison = Double.compare(densityB, densityA); // Descending
            if (densityComparison != 0) {
                return densityComparison;
            }
            
            return a.compareTo(b); // Fall back to default comparison
        };
    }

    /**
     * Creates a comparator for sorting by itemset size.
     * 
     * @param ascending True for ascending order, false for descending
     * @return Comparator for size sorting
     */
    public static Comparator<Itemset> sizeComparator(boolean ascending) {
        return (a, b) -> {
            int sizeComparison = ascending ? 
                Integer.compare(a.size(), b.size()) : 
                Integer.compare(b.size(), a.size());
            
            if (sizeComparison != 0) {
                return sizeComparison;
            }
            
            return a.compareTo(b); // Fall back to default comparison
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Itemset itemset = (Itemset) obj;
        return Double.compare(itemset.expectedUtility, expectedUtility) == 0 &&
               Double.compare(itemset.existentialProbability, existentialProbability) == 0 &&
               Objects.equals(items, itemset.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, expectedUtility, existentialProbability);
    }

    @Override
    public String toString() {
        return String.format("Itemset{items=%s, EU=%.4f, EP=%.4f}", 
                           items, expectedUtility, existentialProbability);
    }

    /**
     * Returns a compact string representation.
     * 
     * @return Compact string representation
     */
    public String toCompactString() {
        List<Integer> sortedItems = new ArrayList<>(items);
        Collections.sort(sortedItems);
        return String.format("{%s}:%.2f", 
                           sortedItems.toString().replaceAll("[\\[\\] ]", ""),
                           expectedUtility);
    }

    /**
     * Returns a detailed string representation for debugging.
     * 
     * @return Detailed string representation
     */
    public String toDetailedString() {
        List<Integer> sortedItems = new ArrayList<>(items);
        Collections.sort(sortedItems);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Itemset{\n");
        sb.append("  items: ").append(sortedItems).append("\n");
        sb.append("  size: ").append(items.size()).append("\n");
        sb.append("  expectedUtility: ").append(String.format("%.6f", expectedUtility)).append("\n");
        sb.append("  existentialProbability: ").append(String.format("%.6f", existentialProbability)).append("\n");
        sb.append("  utilityDensity: ").append(String.format("%.6f", getUtilityDensity())).append("\n");
        sb.append("  combinedScore: ").append(String.format("%.6f", getCombinedScore())).append("\n");
        sb.append("  supportCount: ").append(supportCount == -1 ? "N/A" : supportCount).append("\n");
        sb.append("  discoveryTime: ").append(new Date(discoveryTimestamp)).append("\n");
        sb.append("}");
        
        return sb.toString();
    }

    /**
     * Converts this itemset to a formatted string suitable for file output.
     * 
     * @param delimiter Delimiter to use between fields
     * @return Formatted string for file output
     */
    public String toFileString(String delimiter) {
        List<Integer> sortedItems = new ArrayList<>(items);
        Collections.sort(sortedItems);
        
        StringBuilder itemsStr = new StringBuilder();
        for (int i = 0; i < sortedItems.size(); i++) {
            if (i > 0) itemsStr.append(" ");
            itemsStr.append(sortedItems.get(i));
        }
        
        return String.format("%s%s%.6f%s%.6f%s%d", 
                           itemsStr.toString(), delimiter,
                           expectedUtility, delimiter,
                           existentialProbability, delimiter,
                           items.size());
    }

    /**
     * Creates an itemset from a file string.
     * 
     * @param fileString String representation from file
     * @param delimiter Delimiter used in the file string
     * @return Parsed itemset, or null if parsing fails
     */
    public static Itemset fromFileString(String fileString, String delimiter) {
        try {
            String[] parts = fileString.trim().split(delimiter);
            if (parts.length < 3) {
                return null;
            }
            
            // Parse items
            Set<Integer> items = new HashSet<>();
            String[] itemStrings = parts[0].trim().split("\\s+");
            for (String itemStr : itemStrings) {
                if (!itemStr.isEmpty()) {
                    items.add(Integer.parseInt(itemStr));
                }
            }
            
            // Parse utility and probability
            double expectedUtility = Double.parseDouble(parts[1]);
            double existentialProbability = Double.parseDouble(parts[2]);
            
            return new Itemset(items, expectedUtility, existentialProbability);
            
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Builder class for creating itemsets incrementally.
     */
    public static class Builder {
        private final Set<Integer> items;
        private double expectedUtility;
        private double existentialProbability;
        private int supportCount = -1;

        public Builder() {
            this.items = new HashSet<>();
            this.expectedUtility = 0.0;
            this.existentialProbability = 0.0;
        }

        public Builder(Set<Integer> items) {
            this.items = new HashSet<>(items);
            this.expectedUtility = 0.0;
            this.existentialProbability = 0.0;
        }

        public Builder addItem(int itemId) {
            items.add(itemId);
            return this;
        }

        public Builder addItems(Collection<Integer> itemIds) {
            items.addAll(itemIds);
            return this;
        }

        public Builder setExpectedUtility(double expectedUtility) {
            this.expectedUtility = expectedUtility;
            return this;
        }

        public Builder setExistentialProbability(double existentialProbability) {
            this.existentialProbability = existentialProbability;
            return this;
        }

        public Builder setSupportCount(int supportCount) {
            this.supportCount = supportCount;
            return this;
        }

        public Itemset build() {
            if (supportCount >= 0) {
                return new Itemset(items, expectedUtility, existentialProbability, supportCount);
            } else {
                return new Itemset(items, expectedUtility, existentialProbability);
            }
        }
    }
}