package ootkhuimunpnu.vntm.mining;

import java.util.List;

import ootkhuimunpnu.vntm.db.Itemset;
import ootkhuimunpnu.vntm.db.RawTransaction;

/**
 * Strategy interface for mining engines.
 *
 * <p>A mining engine orchestrates the complete algorithm:
 * preprocessing → single-item evaluation → recursive search → result collection.
    
 * This interface abstracts away the specific search strategy (e.g. depth-first,
 * breadth-first .etc) and allows for different implementations to be
 * swapped in without changing the rest of the codebase.
 *
 * @author Meg
 * @version 5.0
 */
public interface IMiningEngine {

    /**
     * Executes the full HUIM mining algorithm on the given database.
     *
     * @param rawTransactions input uncertain transactions
     * @return list of top-K itemsets sorted by descending expected utility
     */
    List<Itemset> mine(List<RawTransaction> rawTransactions);
}