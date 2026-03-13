package ootkhuimunpnu.vntm.preprocessing;

import java.util.List;

/**
 * Computes suffix sums of positive utilities for items in a transaction.
 *
 * <h3>Purpose</h3>
 * The <em>remaining utility</em> for item at position i is:
 * <pre>
 *   rtu[i] = Σ_{j = i+1}^{n-1}  max(0, profit(j)) · qty(j, T)
 * </pre>
 * i.e., the sum of positive utilities of all items that come <em>after</em>
 * position i in the PTWU-sorted order.
 *
 * <h3>Naïve vs. Optimised</h3>
 * The naïve approach recomputes this for every item: O(n²) per transaction.
 * The suffix-sum approach does it in O(n):
 * <pre>
 *   suffixSums[n-1] = 0
 *   suffixSums[i]   = suffixSums[i+1] + max(0, profit(i+1)) · qty(i+1)
 * </pre>
 *
 * <p>For a database with millions of transactions this reduces preprocessing
 * from minutes to seconds.
 *
 * @author Meg
 * @version 5.0
 */
public class SuffixSumComputer {

    /** Utility class — not instantiable. */
    private SuffixSumComputer() {}

    /**
     * Computes the suffix sum array for a sorted item list from a single transaction.
     *
     * <p>Entry {@code suffixSums[i]} contains the total positive utility
     * of all items at positions &gt; i (i.e., items ranked higher than i in
     * PTWU order that still appear in this transaction).
     *
     * <p>suffixSums[i] becomes the {@code remaining} field in the
     * {@link utility.PULList.Element} for item at position i.
     *
     * @param validItems items sorted by ascending PTWU rank
     * @return suffix-sum array of length {@code validItems.size()}
     */
    public static double[] compute(List<DatabasePreprocessor.ItemData> validItems) {
        int n = validItems.size();
        double[] suffixSums = new double[n];

        // Base case: last item has no items after it
        suffixSums[n - 1] = 0.0;

        // Fill right-to-left: each cell = previous cell + next item's positive utility
        for (int i = n - 2; i >= 0; i--) {
            DatabasePreprocessor.ItemData next = validItems.get(i + 1);
            // Only positive profits contribute to "remaining" utility
            double nextPositiveUtility = (next.profit > 0) ? next.utility : 0.0;
            suffixSums[i] = suffixSums[i + 1] + nextPositiveUtility;
        }

        return suffixSums;
    }
}