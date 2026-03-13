package ootkhuimunpnu.vntm.topk;

import java.util.List;
import java.util.Set;

import ootkhuimunpnu.vntm.db.Itemset;
import ootkhuimunpnu.vntm.utility.PULList;

/**
 * Strategy interface for maintaining the top-K probabilistic high-utility itemsets.
 *
 * <p>Implementations manage an internal data structure holding at most K itemsets
 * with the highest expected utility seen so far. The <em>threshold</em> — the
 * minimum EU among the current top-K — is updated dynamically to enable
 * aggressive pruning: any candidate with EU &lt; threshold can be discarded.
 *
 * @author Meg
 * @version 5.0
 */
public interface ITopKManager {

    /**
     * Attempts to add an itemset to the top-K set.
     *
     * <p>The itemset is accepted if:
     * <ul>
     *   <li>The set has fewer than K entries, or</li>
     *   <li>EU &gt; the current minimum EU in the top-K set (replacing the minimum)</li>
     * </ul>
     *
     * @param items              itemset (set of item IDs)
     * @param expectedUtility    EU(X)
     * @param existentialProbability EP(X)
     * @return {@code true} if the itemset was added or updated
     */
    boolean tryAdd(Set<Integer> items, double expectedUtility, double existentialProbability);

    /**
     * Returns the current mining threshold.
     *
     * <p>When the top-K set contains K itemsets, the threshold equals the
     * K-th highest EU. Before K itemsets are collected, the threshold is 0.0.
     *
     * @return current minimum EU threshold (≥ 0)
     */
    double getThreshold();

    /**
     * Returns the current top-K itemsets in descending EU order.
     *
     * @return list of at most K itemsets
     */
    List<Itemset> getTopK();

    /**
     * Returns the number of itemsets currently held.
     *
     * @return count (≤ K)
     */
    int size();

    /**
     * Returns {@code true} if the top-K set is full (contains K itemsets).
     *
     * @return whether the set is at capacity
     */
    boolean isFull();// ITopKManager.java — add only this snippet:

    /**
     * RUC hook: raise threshold by registering a node before recursing.
     * Default no-op; TopHUITopKManager overrides with leaf-priority logic.
     *
     * @param node the utility list of the currently visited candidate
     * @return true if minUtil was raised
     */
    default boolean tryUpdateThreshold(PULList node) { return false; }
    
}