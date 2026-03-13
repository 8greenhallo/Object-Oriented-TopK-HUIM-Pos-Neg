package ootkhuimunpnu.vntm.preprocessing;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Determines the global ordering of items based on PTWU values.
 *
 * <p>Items are ranked in <em>ascending</em> PTWU order (rank 0 = lowest PTWU).
 * This ordering is critical for two reasons:
 * <ol>
 *   <li>Items with lower PTWU are pruned earlier, minimum wasted join work.</li>
 *   <li>Consistent ordering ensures the sorted-merge join in
 *       {@link utility.PULListJoinOperation} is correct (both lists share
 *       the same transaction order).</li>
 * </ol>
 *
 * <p>Tie-breaking is done by item ID to guarantee a deterministic ordering
 * regardless of HashMap iteration order.
 *
 * @author Meg
 * @version 5.0
 */
public class ItemOrderingStrategy {

    /** Utility class — not instantiable. */
    private ItemOrderingStrategy() {}

    /**
     * Builds a rank map from item ID to integer rank (0 = lowest PTWU).
     *
     * <p>Items with equal PTWU are ordered by ascending item ID.
     *
     * @param itemPTWU map from item ID to PTWU value
     * @return map from item ID to rank (0-based)
     */
    public static Map<Integer, Integer> buildRankMap(Map<Integer, Double> itemPTWU) {
        // Sort entries: primary = ascending PTWU, secondary = ascending item ID (stable tie-break)
        List<Integer> ranked = itemPTWU.entrySet().stream()
                .sorted((a, b) -> {
                    int cmp = Double.compare(a.getValue(), b.getValue());
                    return (cmp != 0) ? cmp : Integer.compare(a.getKey(), b.getKey());
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Integer, Integer> rankMap = new HashMap<>(ranked.size() * 2);
        for (int i = 0; i < ranked.size(); i++) {
            rankMap.put(ranked.get(i), i);
        }
        return rankMap;
    }

    /**
     * Returns items sorted by their rank (ascending PTWU).
     *
     * @param items     items to sort
     * @param itemToRank rank map produced by {@link #buildRankMap}
     * @return sorted list (lowest rank first)
     */
    public static List<Integer> sortByRank(Collection<Integer> items,
                                            Map<Integer, Integer> itemToRank) {
        return items.stream()
                .sorted((a, b) -> {
                    Integer ra = itemToRank.get(a);
                    Integer rb = itemToRank.get(b);
                    if (ra == null && rb == null) return 0;
                    if (ra == null) return 1;   // unranked goes last
                    if (rb == null) return -1;
                    return ra.compareTo(rb);
                })
                .collect(Collectors.toList());
    }
}