package ootkhuimunpnu.vntm.io;

import java.io.*;
import java.util.*;

/**
 * Reads the item profit table from a text file.
 *
 * <h3>Input Format</h3>
 * One item per line in the form:
 * <pre>
 *   item_id profit_value
 * </pre>
 * Profit values may be negative (items with negative utility).
 * Lines beginning with {@code #} and blank lines are ignored.
 *
 * @author Meg
 * @version 5.0
 */
public class ProfitTableReader {

    /**
     * Reads and returns the profit table.
     *
     * @param filename path to the profit table file
     * @return map of item ID → profit value
     * @throws IOException if the file cannot be read or is empty
     */
    public Map<Integer, Double> read(String filename) throws IOException {
        Map<Integer, Double> table = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length != 2) {
                    System.err.printf("Warning: skipping invalid line %d in profit file%n", lineNumber);
                    continue;
                }

                try {
                    int    item   = Integer.parseInt(parts[0]);
                    double profit = Double.parseDouble(parts[1]);
                    table.put(item, profit);
                } catch (NumberFormatException e) {
                    System.err.printf("Warning: number format error at line %d%n", lineNumber);
                }
            }
        }

        if (table.isEmpty()) throw new IOException("Profit table is empty or unreadable: " + filename);
        System.out.println("Loaded profit table: " + table.size() + " items from " + filename);
        return table;
    }
}