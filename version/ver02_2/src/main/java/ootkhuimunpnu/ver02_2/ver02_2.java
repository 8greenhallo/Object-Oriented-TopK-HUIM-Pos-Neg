package ootkhuimunpnu.ver02_2;

import ootkhuimunpnu.ver02_2.core.*;
import ootkhuimunpnu.ver02_2.engine.*;

import java.io.*;
import java.util.*;

/**
 * OOTK-HUIM-UN-PNU algorithm: Top-K High-Utility Itemset Mining from Uncertain Databases
 * with Positive and Negative Utilities
 *
 * Main entry point for the algorithm
 * 
 * MODIFICATIONS:
 * - Fixed RTWU-based item ordering (was incorrectly using item-id)
 * - Implemented log-space probability computation to prevent underflow
 * - O(1) item lookups in transactions using HashMap instead of binary search
 * - All pruning strategies are mathematically proven
 *
 * @author Meg
 * @version 2.2
 */
public class ver02_2 {

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("Usage: ver2_2 <database_file> <profit_file> <k> <min_probability>");
            System.err.println();
            System.err.println("Arguments:");
            System.err.println("  database_file    : Path to transaction database file");
            System.err.println("  profit_file      : Path to item profit table file");
            System.err.println("  k                : Number of top-k itemsets to find");
            System.err.println("  min_probability  : Minimum existential probability threshold (0-1)");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  java ver2.ver2_2 database.txt profits.txt 10 0.1");
            System.exit(1);
        }

        String dbFile = args[0];
        String profitFile = args[1];
        int k = Integer.parseInt(args[2]);
        double minPro = Double.parseDouble(args[3]);

        // Validate inputs
        validateInputs(k, minPro);

        // Read input files
        System.out.println("=== OOTK-HUIM-UN-PNU algorithm ===");
        System.out.println("Reading input files...");
        
        Map<Integer, Double> profits = readProfitTable(profitFile);
        List<Transaction> database = readDatabase(dbFile);

        System.out.println("Loaded " + database.size() + " transactions");
        System.out.println("Loaded " + profits.size() + " items with profits");
        System.out.println();

        // Build configuration
        Configuration config = new Configuration.Builder()
            .setK(k)
            .setMinProbability(minPro)
            .setItemProfits(profits)
            .build();
        
        System.out.println("Configuration: \n" + config.configurationStatistic());
        //System.out.println("Profit statistics: " + config.getProfitStatistics());

        // Create mining engine and run algorithm
        MiningEngine engine = new MiningEngine(config);
        List<Itemset> topK = engine.mine(database);

        // Display results
        displayResults(topK, k);
    }

    /**
     * Validate input parameters
     */
    private static void validateInputs(int k, double minPro) {
        if (k <= 0) {
            System.err.println("Error: k must be a positive integer");
            System.exit(1);
        }

        if (minPro < 0 || minPro > 1) {
            System.err.println("Error: min_probability must be between 0 and 1");
            System.exit(1);
        }
    }

    /**
     * Display mining results
     */
    private static void displayResults(List<Itemset> topK, int k) {
        System.out.println("\n=== Top-" + k + " High-Utility Itemsets ===");
        
        if (topK.isEmpty()) {
            System.out.println("No itemsets found matching the criteria.");
            return;
        }
        
        int rank = 1;
        for (Itemset itemset : topK) {
            // Format itemset for display
            List<Integer> items = new ArrayList<>(itemset.getItems());
            //Collections.sort(items);
            
            System.out.printf("%4d. %s: EU=%4f, EP=%4f\n", 
                            rank++, 
                            items.toString(),
                            itemset.getExpectedUtility(),
                            itemset.getExistentialProbability());
        }
        
        System.out.println();
        System.out.println("Total itemsets found: " + topK.size());
    }

    /**
     * Read profit table from file
     * Format: <item_id> <profit>
     */
    static Map<Integer, Double> readProfitTable(String filename) throws IOException {
        Map<Integer, Double> profits = new HashMap<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNum = 0;
            
            while ((line = br.readLine()) != null) {
                lineNum++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] parts = line.split("\\s+");
                if (parts.length != 2) {
                    System.err.println("Warning: Invalid format at line " + lineNum + 
                                     " in profit file. Expected: <item> <profit>");
                    continue;
                }
                
                try {
                    int item = Integer.parseInt(parts[0]);
                    double profit = Double.parseDouble(parts[1]);
                    profits.put(item, profit);
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Invalid number format at line " + lineNum + 
                                     " in profit file");
                }
            }
        }
        
        if (profits.isEmpty()) {
            throw new IOException("No valid profit data found in file: " + filename);
        }
        
        return profits;
    }

    /**
     * Read database from file
     * Format: <item:quantity:probability> <item:quantity:probability> ...
     */
    static List<Transaction> readDatabase(String filename) throws IOException {
        List<Transaction> database = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            int tid = 1;
            int lineNum = 0;
            
            while ((line = br.readLine()) != null) {
                lineNum++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                Map<Integer, Integer> items = new HashMap<>();
                Map<Integer, Double> probabilities = new HashMap<>();

                String[] entries = line.split("\\s+");
                boolean validTransaction = false;
                
                for (String entry : entries) {
                    String[] parts = entry.split(":");
                    
                    if (parts.length != 3) {
                        System.err.println("Warning: Invalid format at line " + lineNum + 
                                         ". Expected: item:quantity:probability");
                        continue;
                    }
                    
                    try {
                        int item = Integer.parseInt(parts[0]);
                        int quantity = Integer.parseInt(parts[1]);
                        double prob = Double.parseDouble(parts[2]);

                        // Validate probability
                        if (prob < 0 || prob > 1) {
                            System.err.println("Warning: Invalid probability " + prob + 
                                             " at line " + lineNum + ". Must be in [0,1]");
                            continue;
                        }

                        // Validate quantity
                        if (quantity <= 0) {
                            System.err.println("Warning: Invalid quantity " + quantity + 
                                             " at line " + lineNum + ". Must be positive");
                            continue;
                        }

                        items.put(item, quantity);
                        probabilities.put(item, prob);
                        validTransaction = true;
                        
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Invalid number format at line " + lineNum);
                    }
                }

                if (validTransaction && !items.isEmpty()) {
                    database.add(new Transaction(tid++, items, probabilities));
                }
            }
        }
        
        if (database.isEmpty()) {
            throw new IOException("No valid transactions found in file: " + filename);
        }
        
        return database;
    }

    /**
     * Utility method to create a simple test database programmatically
     */
    public static List<Transaction> createTestDatabase() {
        List<Transaction> database = new ArrayList<>();
        
        // Transaction 1: {1:2:0.8, 2:3:0.9}
        Map<Integer, Integer> items1 = new HashMap<>();
        Map<Integer, Double> probs1 = new HashMap<>();
        items1.put(1, 2); probs1.put(1, 0.8);
        items1.put(2, 3); probs1.put(2, 0.9);
        database.add(new Transaction(1, items1, probs1));
        
        // Transaction 2: {1:1:0.7, 3:2:0.6}
        Map<Integer, Integer> items2 = new HashMap<>();
        Map<Integer, Double> probs2 = new HashMap<>();
        items2.put(1, 1); probs2.put(1, 0.7);
        items2.put(3, 2); probs2.put(3, 0.6);
        database.add(new Transaction(2, items2, probs2));
        
        // Transaction 3: {2:2:0.85, 3:1:0.75}
        Map<Integer, Integer> items3 = new HashMap<>();
        Map<Integer, Double> probs3 = new HashMap<>();
        items3.put(2, 2); probs3.put(2, 0.85);
        items3.put(3, 1); probs3.put(3, 0.75);
        database.add(new Transaction(3, items3, probs3));
        
        return database;
    }

    /**
     * Utility method to create a simple test profit table
     */
    public static Map<Integer, Double> createTestProfits() {
        Map<Integer, Double> profits = new HashMap<>();
        profits.put(1, 5.0);
        profits.put(2, 3.0);
        profits.put(3, -2.0);  // Negative utility item
        profits.put(4, 4.0);
        return profits;
    }
}