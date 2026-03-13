package ootkhuimunpnu.vntm.io;

import java.io.*;
import java.util.*;

import ootkhuimunpnu.vntm.db.RawTransaction;

/**
 * Reads a transaction database from a text file.
 *
 * <h3>Input Format</h3>
 * One transaction per line. Items are space-separated, each in the form:
 * <pre>
 *   item_id:quantity:probability
 * </pre>
 * Example line:
 * <pre>
 *   1:2:0.8 3:1:0.9 5:3:0.7
 * </pre>
 * Lines beginning with {@code #} and blank lines are ignored.
 *
 * @author Meg
 * @version 5.0
 */
public class TransactionFileReader {

    /**
     * Reads and returns all valid transactions from the file.
     *
     * @param filename path to the transaction database file
     * @return list of raw transactions (TID is 1-based, assigned in order)
     * @throws IOException if the file cannot be read or is empty
     */
    public List<RawTransaction> read(String filename) throws IOException {
        List<RawTransaction> database = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int tid        = 1;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                Map<Integer, Integer> items         = new HashMap<>();
                Map<Integer, Double>  probabilities = new HashMap<>();
                boolean valid = true;

                for (String entry : line.split("\\s+")) {
                    String[] parts = entry.split(":");
                    if (parts.length != 3) {
                        System.err.printf("Warning: invalid entry at line %d: '%s'%n",
                                lineNumber, entry);
                        valid = false;
                        break;
                    }
                    try {
                        int    item  = Integer.parseInt(parts[0]);
                        int    qty   = Integer.parseInt(parts[1]);
                        double prob  = Double.parseDouble(parts[2]);

                        if (prob < 0.0 || prob > 1.0) {
                            System.err.printf("Warning: probability out of [0,1] at line %d%n",
                                    lineNumber);
                            valid = false;
                            break;
                        }
                        items.put(item, qty);
                        probabilities.put(item, prob);
                    } catch (NumberFormatException e) {
                        System.err.printf("Warning: number format error at line %d%n", lineNumber);
                        valid = false;
                        break;
                    }
                }

                if (valid && !items.isEmpty()) {
                    database.add(new RawTransaction(tid++, items, probabilities));
                }
            }
        }

        if (database.isEmpty()) throw new IOException("Database is empty or unreadable: " + filename);
        System.out.println("Loaded database: " + database.size() + " transactions from " + filename);
        return database;
    }
}