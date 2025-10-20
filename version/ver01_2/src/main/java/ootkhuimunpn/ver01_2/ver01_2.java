import engine.*;
import core.*;
import java.io.*;
import java.util.*;

/**
 * Main class for OOTK-HUIM-U± algorithm - OOP Framework Version 1.1
 * 
 * This is a complete refactoring of the original ver1_1.java into a proper
 * object-oriented framework suitable for academic research and publication.
 * 
 * Key improvements over the baseline:
 * - Clean separation of concerns with dedicated classes
 * - Comprehensive configuration management
 * - Robust error handling and validation
 * - Detailed logging and statistics
 * - Maintainable and extensible architecture
 * 
 * @author Meg
 * @version 1.2
 */
public class ver01_2 {
    
    /**
     * Main entry point for the OOTK-HUIM-U± algorithm.
     * 
     * @param args Command line arguments: <database_file> <profit_file> <k> <min_probability>
     */
    public static void main(String[] args) {
        try {
            // Validate command line arguments
            if (args.length != 4) {
                printUsage();
                System.exit(1);
            }
            
            // Parse command line arguments
            String databaseFile = args[0];
            String profitFile = args[1];
            int k = parseInteger(args[2], "k");
            double minProbability = parseDouble(args[3], "min_probability");
            
            // Print algorithm header
            printHeader();
            
            // Execute mining process
            executeMining(databaseFile, profitFile, k, minProbability);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Executes the complete mining process.
     */
    private static void executeMining(String databaseFile, String profitFile, 
                                    int k, double minProbability) throws IOException {
        
        // Load data files
        System.out.println("Loading data files...");
        Map<Integer, Double> itemProfits = loadProfitTable(profitFile);
        List<Transaction> database = loadDatabase(databaseFile, itemProfits);
        
        System.out.println(String.format("Loaded %d transactions and %d item profits", 
                                        database.size(), itemProfits.size()));
        
        // Build configuration
        Configuration config = new Configuration.Builder()
            .setK(k)
            .setMinProbability(minProbability)
            .setItemProfits(itemProfits)
            .enableDetailedLogging(false)  // Set to true for debugging
            .enableMemoryMonitoring(true)
            .enableAdvancedPruning(true)
            .includeDetailedStatistics(true)
            .build();
        
        System.out.println("Configuration: " + config);
        System.out.println("Profit statistics: " + config.getProfitStatistics());
        
        // Create and execute mining engine
        MiningEngine engine = new MiningEngine(config);
        List<Itemset> results = engine.mine(database);
        
        // Display results
        displayResults(results, k);
        
        // Display detailed statistics
        displayStatistics(engine.getStatistics());
    }
    
    /**
     * Loads the profit table from file.
     * Format: <item_id> <profit>
     */
    private static Map<Integer, Double> loadProfitTable(String filename) throws IOException {
        Map<Integer, Double> profits = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }
                
                String[] parts = line.split("\\s+");
                if (parts.length != 2) {
                    throw new IOException(String.format(
                        "Invalid profit file format at line %d: expected 2 values, found %d", 
                        lineNumber, parts.length));
                }
                
                try {
                    int item = Integer.parseInt(parts[0]);
                    double profit = Double.parseDouble(parts[1]);
                    profits.put(item, profit);
                } catch (NumberFormatException e) {
                    throw new IOException(String.format(
                        "Invalid number format at line %d: %s", lineNumber, e.getMessage()));
                }
            }
        }
        
        if (profits.isEmpty()) {
            throw new IOException("Profit file is empty or contains no valid entries");
        }
        
        return profits;
    }
    
    /**
     * Loads the transaction database from file.
     * Format: <item_id>:<quantity>:<probability> <item_id>:<quantity>:<probability> ...
     */
    private static List<Transaction> loadDatabase(String filename, 
                                                Map<Integer, Double> itemProfits) throws IOException {
        List<Transaction> database = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int tid = 1;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }
                
                try {
                    Transaction transaction = parseTransaction(tid, line, itemProfits, lineNumber);
                    if (transaction != null) {
                        database.add(transaction);
                        tid++;
                    }
                } catch (Exception e) {
                    throw new IOException(String.format(
                        "Error parsing transaction at line %d: %s", lineNumber, e.getMessage()));
                }
            }
        }
        
        if (database.isEmpty()) {
            throw new IOException("Database file is empty or contains no valid transactions");
        }
        
        return database;
    }
    
    /**
     * Parses a single transaction line.
     */
    private static Transaction parseTransaction(int tid, String line, 
                                             Map<Integer, Double> itemProfits, 
                                             int lineNumber) throws IOException {
        Map<Integer, Integer> items = new HashMap<>();
        Map<Integer, Double> probabilities = new HashMap<>();
        
        String[] entries = line.split("\\s+");
        
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                throw new IOException(String.format(
                    "Invalid entry format '%s': expected item:quantity:probability", entry));
            }
            
            try {
                int item = Integer.parseInt(parts[0]);
                int quantity = Integer.parseInt(parts[1]);
                double probability = Double.parseDouble(parts[2]);
                
                // Validate values
                if (quantity <= 0) {
                    throw new IOException(String.format("Quantity must be positive for item %d", item));
                }
                
                if (probability <= 0.0 || probability > 1.0) {
                    throw new IOException(String.format(
                        "Probability must be in (0.0, 1.0] for item %d", item));
                }
                
                // Check if item has profit information
                if (!itemProfits.containsKey(item)) {
                    System.out.println(String.format(
                        "Warning: Item %d in transaction %d has no profit information (skipping)", 
                        item, tid));
                    continue;
                }
                
                items.put(item, quantity);
                probabilities.put(item, probability);
                
            } catch (NumberFormatException e) {
                throw new IOException(String.format("Invalid number in entry '%s': %s", entry, e.getMessage()));
            }
        }
        
        if (items.isEmpty()) {
            System.out.println(String.format("Warning: Transaction %d contains no valid items (skipping)", tid));
            return null;
        }
        
        return new Transaction(tid, items, probabilities, itemProfits);
    }
    
    /**
     * Displays the mining results.
     */
    private static void displayResults(List<Itemset> results, int k) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println(String.format("TOP-%d HIGH-UTILITY ITEMSETS", k));
        System.out.println("=".repeat(60));
        
        if (results.isEmpty()) {
            System.out.println("No high-utility itemsets found matching the criteria.");
            return;
        }
        
        for (int i = 0; i < results.size(); i++) {
            Itemset itemset = results.get(i);
            System.out.println(String.format("%3d. %s", i + 1, itemset));
        }
        
        System.out.println("=".repeat(60));
    }
    
    /**
     * Displays detailed mining statistics.
     */
    private static void displayStatistics(MiningEngine.MiningStatistics stats) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("MINING STATISTICS");
        System.out.println("=".repeat(60));
        
        System.out.println(String.format("Execution time: %,d ms", stats.getExecutionTimeMs()));
        System.out.println(String.format("Peak memory usage: %,d MB", stats.getPeakMemoryUsage() / 1024 / 1024));
        System.out.println(String.format("Memory checks performed: %,d", stats.getMemoryChecks()));
        System.out.println();
        
        System.out.println("SEARCH SPACE STATISTICS:");
        System.out.println(String.format("  Utility lists created: %,d", stats.getUtilityListsCreated()));
        System.out.println(String.format("  Candidates generated: %,d", stats.getCandidatesGenerated()));
        System.out.println();
        
        System.out.println("PRUNING EFFECTIVENESS:");
        PruningStrategy.PruningStatistics pruningStats = stats.getPruningStats();
        System.out.println(String.format("  Total candidates evaluated: %,d", pruningStats.getTotalEvaluated()));
        System.out.println(String.format("  Candidates pruned: %,d (%.2f%%)", 
                                        pruningStats.getTotalPruned(), 
                                        pruningStats.getPruningRatio() * 100));
        System.out.println(String.format("    - Expected utility pruning: %,d", pruningStats.getPrunedByEU()));
        System.out.println(String.format("    - Existential probability pruning: %,d", pruningStats.getPrunedByEP()));
        System.out.println(String.format("    - Early termination: %,d", pruningStats.getPrunedByET()));
        System.out.println();
        
        System.out.println("TOP-K MANAGEMENT:");
        TopKManager.Statistics topKStats = stats.getTopKStats();
        System.out.println(String.format("  Results found: %d", topKStats.getCount()));
        if (topKStats.getCount() > 0) {
            System.out.println(String.format("  Utility range: [%.4f, %.4f]", 
                                            topKStats.getMinUtility(), topKStats.getMaxUtility()));
            System.out.println(String.format("  Average utility: %.4f", topKStats.getAvgUtility()));
            System.out.println(String.format("  Average probability: %.4f", topKStats.getAvgProbability()));
        }
        
        System.out.println("=".repeat(60));
    }
    
    /**
     * Prints the algorithm header information.
     */
    private static void printHeader() {
        System.out.println("=".repeat(80));
        System.out.println("OOTK-HUIM-U± : Object-Oriented Top-K High-Utility Itemset Mining");
        System.out.println("           from Uncertain Databases with Positive and Negative Utilities");
        System.out.println();
        System.out.println("Version 1.1");
        System.out.println();
        System.out.println("Key Features:");
        System.out.println("• Clean OOP architecture with separation of concerns");
        System.out.println("• Mathematically proven pruning strategies");
        System.out.println("• Comprehensive logging and statistics");
        System.out.println("• Robust error handling and validation");
        System.out.println("• RTWU-based optimization");
        System.out.println("• Log-space probability computation for numerical stability");
        System.out.println("=".repeat(80));
        System.out.println();
    }
    
    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: java ver1_1 <database_file> <profit_file> <k> <min_probability>");
        System.out.println();
        System.out.println("Parameters:");
        System.out.println("  database_file    : Path to transaction database file");
        System.out.println("  profit_file      : Path to item profit table file");
        System.out.println("  k                : Number of top itemsets to find (positive integer)");
        System.out.println("  min_probability  : Minimum existential probability threshold (0.0 to 1.0)");
        System.out.println();
        System.out.println("File Formats:");
        System.out.println("  Database: item_id:quantity:probability item_id:quantity:probability ...");
        System.out.println("  Profits:  item_id profit");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java ver1_1 database.txt profits.txt 10 0.1");
    }
    
    /**
     * Safely parses an integer from string with error handling.
     */
    private static int parseInteger(String value, String paramName) {
        try {
            int result = Integer.parseInt(value);
            if (result <= 0) {
                throw new IllegalArgumentException(paramName + " must be positive");
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value for " + paramName + ": " + value);
        }
    }
    
    /**
     * Safely parses a double from string with error handling.
     */
    private static double parseDouble(String value, String paramName) {
        try {
            double result = Double.parseDouble(value);
            if (paramName.equals("min_probability") && (result < 0.0 || result > 1.0)) {
                throw new IllegalArgumentException("min_probability must be between 0.0 and 1.0");
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double value for " + paramName + ": " + value);
        }
    }
    
    /**
     * Demonstrates the OOP framework with a sample dataset.
     * This method can be used for testing and validation.
     */
    public static void runDemo() {
        try {
            System.out.println("Running OOTK-HUIM-U± Demo...");
            
            // Create sample item profits
            Map<Integer, Double> profits = new HashMap<>();
            profits.put(1, 5.0);   // Positive utility item
            profits.put(2, -2.0);  // Negative utility item
            profits.put(3, 3.0);   // Positive utility item
            profits.put(4, 8.0);   // High positive utility item
            profits.put(5, -1.0);  // Negative utility item
            
            // Create sample transactions
            List<Transaction> database = new ArrayList<>();
            
            // Transaction 1: {1:2:0.8, 2:1:0.6, 3:3:0.9}
            Map<Integer, Integer> items1 = new HashMap<>();
            Map<Integer, Double> probs1 = new HashMap<>();
            items1.put(1, 2); probs1.put(1, 0.8);
            items1.put(2, 1); probs1.put(2, 0.6);
            items1.put(3, 3); probs1.put(3, 0.9);
            database.add(new Transaction(1, items1, probs1, profits));
            
            // Transaction 2: {1:1:0.7, 4:2:0.95, 5:1:0.4}
            Map<Integer, Integer> items2 = new HashMap<>();
            Map<Integer, Double> probs2 = new HashMap<>();
            items2.put(1, 1); probs2.put(1, 0.7);
            items2.put(4, 2); probs2.put(4, 0.95);
            items2.put(5, 1); probs2.put(5, 0.4);
            database.add(new Transaction(2, items2, probs2, profits));
            
            // Transaction 3: {2:2:0.5, 3:1:0.8, 4:1:0.9}
            Map<Integer, Integer> items3 = new HashMap<>();
            Map<Integer, Double> probs3 = new HashMap<>();
            items3.put(2, 2); probs3.put(2, 0.5);
            items3.put(3, 1); probs3.put(3, 0.8);
            items3.put(4, 1); probs3.put(4, 0.9);
            database.add(new Transaction(3, items3, probs3, profits));
            
            // Configure and run mining
            Configuration config = new Configuration.Builder()
                .setK(5)
                .setMinProbability(0.3)
                .setItemProfits(profits)
                .enableDetailedLogging(true)
                .build();
            
            MiningEngine engine = new MiningEngine(config);
            List<Itemset> results = engine.mine(database);
            
            // Display demo results
            System.out.println("\nDemo Results:");
            displayResults(results, 5);
            displayStatistics(engine.getStatistics());
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}