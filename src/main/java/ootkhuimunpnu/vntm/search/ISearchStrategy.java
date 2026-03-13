package ootkhuimunpnu.vntm.search;

import java.util.*;

import ootkhuimunpnu.vntm.pruning.IPruningStrategy;
import ootkhuimunpnu.vntm.topk.ITopKManager;
import ootkhuimunpnu.vntm.utility.PULList;
import ootkhuimunpnu.vntm.monitoring.PerformanceProfiler;
import ootkhuimunpnu.vntm.monitoring.StatisticsCollector;

/**
 * Strategy interface for itemset search traversal.
 *
 * <p>Implementations control <em>how</em> the candidate itemset space is
 * explored: 
 * 
 * depth-first,
 * breadth-first,
 * best-first.
 * 
 * The engine calls {@link #search} with a prefix utility list 
 * and a list of viable extensions,
 * and the strategy performs all recursive or iterative expansion internally.
 *
 * <p>Strategies must:
 * <ul>
 *   <li>Invoke {@link pruning.IPruningStrategy} to gate each candidate</li>
 *   <li>Call {@link ITopKManager#tryAdd} for qualifying itemsets</li>
 *   <li>Update {@link StatisticsCollector} and {@link PerformanceProfiler}
 *       for accurate benchmarking</li>
 * </ul>
 *
 * @author Meg
 * @version 5.0
 */
public interface ISearchStrategy {

    /**
     * Explores all extensions of {@code prefix} and updates the top-K manager.
     *
     * @param prefix     current prefix utility list (itemset A)
     * @param extensions candidate extension utility lists (items i &gt; A)
     * @param topK       top-K manager for recording results and querying threshold
     * @param pruning    pruning strategy for gating candidates
     * @param stats      statistics collector for pruning/candidate counts
     * @param profiler   performance profiler (handles exclusive timing per component)
     */
    void search(PULList prefix,
                List<PULList> extensions,
                ITopKManager topK,
                IPruningStrategy pruning,
                StatisticsCollector stats,
                PerformanceProfiler profiler);
}