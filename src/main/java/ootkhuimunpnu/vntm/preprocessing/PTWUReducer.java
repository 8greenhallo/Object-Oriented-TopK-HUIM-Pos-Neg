package ootkhuimunpnu.vntm.preprocessing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.db.RawTransaction;
import ootkhuimunpnu.vntm.utility.ProfitTable;

/**
 * Computes the Positive Transaction-Weighted Utility (PTWU) for every item
 * in a single pass over the database.
 *
 * <h3>Mathematical Definition</h3>
 * <pre>
 *   PTU(T)    = Σ_{i∈T, profit(i)>0}  profit(i) · qty(i,T)
 *   PTWU(i)   = Σ_{T∈D : i∈T, P(i,T)>0}  PTU(T)
 * </pre>
 *
 * <p>PTWU provides an upper bound for the expected utility of any itemset
 * that contains item i:
 * <pre>
 *   EU(X) ≤ PTWU(i)  for all X ∋ i
 * </pre>
 * Therefore, if PTWU(i) &lt; current threshold, item i and all its
 * supersets can be safely pruned without missing any top-K results.
 *
 * <h3>Optimisation</h3>
 * The original two-pass approach iterates over T twice (once for PTU,
 * once to update PTWU).  This implementation computes PTU inline during
 * a single scan of each transaction's items, reducing complexity from
 * O(2·|DB|·T_avg) to O(|DB|·T_avg).
 *
 * @author Meg
 * @version 5.0
 */
public class PTWUReducer {

    private final AlgorithmConfig config;
    private final ProfitTable profitTable;

    /**
     * @param config      algorithm configuration
     * @param profitTable global item → profit map
     */
    public PTWUReducer(AlgorithmConfig config, ProfitTable profitTable) {
    //public PTWUReducer(AlgorithmConfig config, ProfitTable profitTable, ITopKManager topKManager) {
        this.config      = config;
        this.profitTable = profitTable;
        //this.topKManager = topKManager;   // Initialize
    }

    /**
     * Computes PTWU for every item that appears at least once with positive
     * probability across all transactions.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>For each transaction T, compute PTU(T) = Σ positive utilities.</li>
     *   <li>For each item i in T with P(i,T) &gt; 0, add PTU(T) to PTWU(i).</li>
     * </ol>
     *
     * @param rawTransactions list of raw transactions
     * @return map from item ID → PTWU value
     */
    public Map<Integer, Double> computePTWU(List<RawTransaction> rawTransactions) {
        Map<Integer, Double> ptwuMap = new HashMap<>();

        for (RawTransaction trans : rawTransactions) {

            // ------------------------------------------------------------------
            // Step 1: Compute PTU(T) — sum of positive-profit utilities in T
            // ------------------------------------------------------------------
            double ptu = 0.0;
            //double etu = 0.0;     // Calculate Expected Transaction Utility
            for (Map.Entry<Integer, Integer> entry : trans.getItems().entrySet()) {
                int item    = entry.getKey();
                int qty     = entry.getValue();
                double profit = profitTable.getProfit(item);

                // Only positive profits contribute to the "potential" utility
                if (profit > AlgorithmConfig.EPSILON) {
                    ptu += profit * qty;
                    
                    // Calculate the ETU (multiplied by probability) as required by FTKHUIM.
                    //Double prob = trans.getProbabilities().get(item);
                    //if (prob != null && prob > AlgorithmConfig.EPSILON) {
                    //    etu += profit * qty * prob;
                    //}
                }
            }

            // Seed the FTK threshold manager with the ETU of this transaction, 
            // which is an upper bound on the utility of any itemset contained in it.
            //if (topKManager instanceof FTKTopKManager) {
            //    ((FTKTopKManager) topKManager).seedTransactionUtility(etu);
            //}

            // ------------------------------------------------------------------
            // Step 2: Distribute PTU(T) to each item with positive probability
            // ------------------------------------------------------------------
            for (Map.Entry<Integer, Integer> entry : trans.getItems().entrySet()) {
                int item = entry.getKey();
                Double prob = trans.getProbabilities().get(item);

                // Items with zero / null probability don't actually appear
                if (prob != null && prob > AlgorithmConfig.EPSILON) {
                    // PTWU(i) += PTU(T)
                    ptwuMap.merge(item, ptu, Double::sum);
                }
            }
        }

        return ptwuMap;
    }
}