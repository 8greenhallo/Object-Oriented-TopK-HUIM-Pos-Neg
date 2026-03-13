package ootkhuimunpnu.vntm.io;

import java.util.List;
import java.util.Map;

import ootkhuimunpnu.vntm.db.RawTransaction;

/**
 * Validates loaded database and profit table data before mining.
 *
 * <p>Catches common data issues early so the algorithm doesn't fail
 * silently mid-run with unexpected behaviour.
 *
 * @author Meg
 * @version 5.0
 */
public class DataValidator {

    /**
     * Validates the transaction database.
     *
     * @param database loaded transactions
     * @throws IllegalArgumentException if any validation fails
     */
    public void validateDatabase(List<RawTransaction> database) {
        if (database == null || database.isEmpty()) {
            throw new IllegalArgumentException("Database must not be null or empty.");
        }
        for (RawTransaction t : database) {
            for (Map.Entry<Integer, Double> e : t.getProbabilities().entrySet()) {
                double p = e.getValue();
                if (p < 0.0 || p > 1.0) {
                    throw new IllegalArgumentException(
                            "Probability out of [0,1] in transaction " + t.getTid() +
                            " for item " + e.getKey() + ": " + p);
                }
            }
        }
    }

    /**
     * Validates the profit table.
     *
     * @param profitTable item → profit map
     * @throws IllegalArgumentException if the table is empty
     */
    public void validateProfitTable(Map<Integer, Double> profitTable) {
        if (profitTable == null || profitTable.isEmpty()) {
            throw new IllegalArgumentException("Profit table must not be null or empty.");
        }
    }
}