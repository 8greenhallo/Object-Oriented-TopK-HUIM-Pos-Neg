package ootkhuimunpnu.vntm.preprocessing;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.db.RawTransaction;
import ootkhuimunpnu.vntm.utility.PULList;
import ootkhuimunpnu.vntm.utility.ProfitTable;

import java.util.*;

/**
 * Facade / Orchestrator for the two-pass database preprocessing pipeline.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li><b>Pass 1 — PTWU Reduction</b> ({@link PTWUReducer}):
 *       Compute PTWU(i) for every item i and build a global rank ordering
 *       (ascending PTWU). Items with PTWU &lt; initial threshold may be
 *       discarded early.
 *   </li>
 *   <li><b>Global Ordering</b> ({@link ItemOrderingStrategy}):
 *       Map every item to an integer rank so that sorted-array access
 *       is possible in O(1) during join.
 *   </li>
 *   <li><b>Pass 2 — Utility List Construction</b>:
 *       For each transaction, sort items by rank, then use the
 *       {@link SuffixSumComputer} to compute remaining utilities in O(|T|)
 *       instead of O(|T|²), and emit one {@link PULList.Element} per item.
 *   </li>
 *   <li><b>Filtering</b>:
 *       Drop any single-item utility list whose existential probability
 *       is below {@code minProbability}.
 *   </li>
 * </ol>
 *
 * @author Meg
 * @version 5.0
 */
public class DatabasePreprocessor {

    private final AlgorithmConfig config;
    private final ProfitTable profitTable;

    // Results set by preprocess()
    private Map<Integer, Integer> itemToRank;
    private Map<Integer, Double> itemPTWU;

    /**
     * Constructs the preprocessor.
     *
     * @param config      algorithm configuration (K, minProbability, …)
     * @param profitTable global profit table
     */
    public DatabasePreprocessor(AlgorithmConfig config, ProfitTable profitTable) {
        this.config      = config;
        this.profitTable = profitTable;
    }

    /**
     * Runs the full preprocessing pipeline and returns the single-item
     * utility lists ready for mining.
     *
     * @param rawTransactions raw transactions from the database file
     * @return map from item ID → utility list (only items satisfying EP ≥ minProbability)
     */
    public Map<Integer, PULList> preprocess(List<RawTransaction> rawTransactions) {

        // =====================================================================
        // Pass 1: Compute PTWU and build rank ordering
        // =====================================================================
        PTWUReducer reducer = new PTWUReducer(config, profitTable);
        this.itemPTWU   = reducer.computePTWU(rawTransactions);
        this.itemToRank = ItemOrderingStrategy.buildRankMap(itemPTWU);
        // SAFEGUARD: ensure strict total order (no duplicate rank)
        if (new HashSet<>(itemToRank.values()).size() != itemToRank.size()) {
            throw new IllegalStateException("Rank map contains duplicate ranks.");
        }

        // =====================================================================
        // Pass 2: Build utility lists with O(|T|) suffix sum preprocessing
        // =====================================================================
        // temp storage: item → accumulated element list
        Map<Integer, List<PULList.Element>> tempElements = new HashMap<>();

        for (RawTransaction rawTrans : rawTransactions) {
            // --- Extract items present in our rank map ---
            List<ItemData> validItems = extractAndSortValidItems(rawTrans);
            if (validItems.isEmpty()) continue;

            // --- Suffix sums: rtu[i] = Σ_{j>i, profit(j)>0} profit(j)·qty(j,T) ---
            double[] suffixSums = SuffixSumComputer.compute(validItems);

            // --- Emit one element per valid item ---
            for (int i = 0; i < validItems.size(); i++) {
                ItemData id = validItems.get(i);
                if (id.logProb <= AlgorithmConfig.LOG_EPSILON) continue; // negligible prob

                tempElements
                    .computeIfAbsent(id.item, k -> new ArrayList<>())
                    .add(new PULList.Element(
                            rawTrans.getTid(),
                            id.utility,
                            suffixSums[i], // O(1) lookup of remaining utility
                            id.logProb
                    ));
            }
        }

        // =====================================================================
        // Build final PULLists and filter by existential probability
        // =====================================================================
        Map<Integer, PULList> singleItemLists = new HashMap<>();
        for (Map.Entry<Integer, List<PULList.Element>> entry : tempElements.entrySet()) {
            int item = entry.getKey();
            List<PULList.Element> elements = entry.getValue();

            if (elements.isEmpty()) continue;

            Set<Integer> itemset = Collections.singleton(item);
            double ptwu = itemPTWU.getOrDefault(item, 0.0);
            PULList ul = new PULList(itemset, elements, ptwu);

            // Filter: EP(X) >= minProbability
            if (ul.getExistentialProbability() >= config.getMinProbability() - AlgorithmConfig.EPSILON) {
                singleItemLists.put(item, ul);
            }
        }

        return singleItemLists;
    }

    /**
     * Returns the item → PTWU map (available after {@link #preprocess}).
     *
     * @return PTWU map
     */
    public Map<Integer, Double> getItemPTWU() {
        return Collections.unmodifiableMap(itemPTWU);
    }

    /**
     * Returns the item → rank map (available after {@link #preprocess}).
     *
     * @return rank map
     */
    public Map<Integer, Integer> getItemToRank() {
        return Collections.unmodifiableMap(itemToRank);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Extracts items from a transaction that appear in the rank map
     * (i.e., survived PTWU filtering), sorts them by ascending rank,
     * and wraps each in an {@link ItemData} record.
     *
     * @param rawTrans raw transaction
     * @return sorted list of valid items
     */
    private List<ItemData> extractAndSortValidItems(RawTransaction rawTrans) {
        List<ItemData> result = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : rawTrans.getItems().entrySet()) {
            int item  = entry.getKey();
            int qty   = entry.getValue();

            if (!itemToRank.containsKey(item)) continue; // not ranked → skip

            Double profit = profitTable.asMap().get(item);
            Double prob   = rawTrans.getProbabilities().get(item);

            if (profit == null || prob == null || prob <= AlgorithmConfig.EPSILON) continue;

            // Convert probability to log-space
            double logProb = Math.log(prob);  // prob > EPSILON guaranteed above
            result.add(new ItemData(item, qty, profit, logProb));
        }

        // Sort by ascending PTWU rank (lower rank = processed first)
        result.sort((a, b) -> Integer.compare(itemToRank.get(a.item), itemToRank.get(b.item)));
        return result;
    }

    // =========================================================================
    // Package-visible data carrier (used by SuffixSumComputer)
    // =========================================================================

    /**
     * Lightweight holder for item data during preprocessing.
     * Used by both {@link DatabasePreprocessor} and {@link SuffixSumComputer}.
     */
    static class ItemData {
        final int item;
        final int quantity;
        final double profit;
        final double utility;   // profit * quantity
        final double logProb;

        ItemData(int item, int quantity, double profit, double logProb) {
            this.item     = item;
            this.quantity = quantity;
            this.profit   = profit;
            this.utility  = profit * quantity;
            this.logProb  = logProb;
        }
    }
}