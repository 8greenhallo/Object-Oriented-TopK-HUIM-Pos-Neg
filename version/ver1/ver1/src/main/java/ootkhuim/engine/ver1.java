package engine;

import core.*;
import java.util.*;
import java.io.*;
import java.time.*;

/**
 * Main entry point for the OOTK-HUIM algorithm.
 * Handles command-line arguments, file I/O, and orchestrates the mining process.
 * 
 * @author Meg
 * @version 1.0
 */
public class ver1_0 {
    
    private static final String VERSION = "1.0";
    private static final String ALGORITHM_NAME = "OOTK-HUIM";
    
    /**
     * Main method - entry point for the application.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            CommandLineArgs cmdArgs = parseCommandLineArguments(args);
            
            if (cmdArgs == null) {
                printUsageAndExit();
                return;
            }
            
            // Display algorithm information
            printAlgorithmInfo(cmdArgs);
            
            // Load input data
            Map<Integer, Double> itemProfits = loadItemProfits(cmdArgs.profitFile);
            List<Transaction> database = loadDatabase(cmdArgs.databaseFile);
            
            // Create configuration
            Configuration config = createConfiguration(cmdArgs, itemProfits);
            
            // Validate configuration
            config.validate();
            
            // Execute mining algorithm
            MiningEngine engine = new MiningEngine(config);
            List<Itemset> results = engine.mine(database);
            
            // Export results
            exportResults(results, cmdArgs, engine);
            
            System.out.println("\nMining completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during mining: " + e.getMessage());
            if (isDebugMode()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    /**
     * Parses command line arguments.
     * 
     * @param args Command line arguments
     * @return Parsed arguments object, or null if parsing failed
     */
    private static CommandLineArgs parseCommandLineArguments(String[] args) {
        if (args.length < 4) {
            return null;
        }
        
        CommandLineArgs cmdArgs = new CommandLineArgs();
        
        try {
            cmdArgs.databaseFile = args[0];
            cmdArgs.profitFile = args[1];
            cmdArgs.k = Integer.parseInt(args[2]);
            cmdArgs.minProbability = Double.parseDouble(args[3]);
            
            // Optional arguments
            for (int i = 4; i < args.length; i++) {
                String arg = args[i];
                
                if (arg.equals("-o") || arg.equals("--output")) {
                    if (i + 1 < args.length) {
                        cmdArgs.outputFile = args[++i];
                    }
                } else if (arg.equals("-t") || arg.equals("--threads")) {
                    if (i + 1 < args.length) {
                        cmdArgs.maxThreads = Integer.parseInt(args[++i]);
                    }
                } else if (arg.equals("-v") || arg.equals("--verbose")) {
                    cmdArgs.verbose = true;
                } else if (arg.equals("-d") || arg.equals("--debug")) {
                    cmdArgs.debug = true;
                } else if (arg.equals("--no-object-oriented")) {
                    cmdArgs.disableObjectOriented = true;
                } else if (arg.equals("-c") || arg.equals("--config")) {
                    if (i + 1 < args.length) {
                        cmdArgs.configFile = args[++i];
                    }
                } else if (arg.equals("--export-stats")) {
                    cmdArgs.exportStatistics = true;
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printUsageAndExit();
                    return null;
                }
            }
            
            return cmdArgs;
            
        } catch (NumberFormatException e) {
            System.err.println("Error parsing numeric arguments: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates configuration from command line arguments.
     * 
     * @param cmdArgs Command line arguments
     * @param itemProfits Item profit mapping
     * @return Configuration object
     */
    private static Configuration createConfiguration(CommandLineArgs cmdArgs, Map<Integer, Double> itemProfits) 
            throws IOException {
        Configuration config;
        
        // Load from config file if specified
        if (cmdArgs.configFile != null) {
            config = Configuration.loadFromFile(cmdArgs.configFile);
            // Override with command line values
            config.setK(cmdArgs.k);
            config.setMinProbability(cmdArgs.minProbability);
            config.setItemProfits(itemProfits);
        } else {
            // Create from command line arguments
            config = new Configuration(cmdArgs.k, cmdArgs.minProbability, itemProfits);
        }
        
        if (cmdArgs.verbose || cmdArgs.debug) {
            config.setDetailedLoggingEnabled(true);
        }
        
        if (cmdArgs.debug) {
            config.setPerformanceMonitoringEnabled(true);
            config.setIntermediateResultsExportEnabled(true);
        }
        
        return config;
    }
    
    /**
     * Loads item profit table from file.
     * 
     * @param filename Profit file path
     * @return Map of item IDs to profit values
     * @throws IOException if file cannot be read
     */
    private static Map<Integer, Double> loadItemProfits(String filename) throws IOException {
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
                    System.err.printf("Warning: Invalid format in profit file at line %d: %s\n", 
                                    lineNumber, line);
                    continue;
                }
                
                try {
                    int itemId = Integer.parseInt(parts[0]);
                    double profit = Double.parseDouble(parts[1]);
                    profits.put(itemId, profit);
                } catch (NumberFormatException e) {
                    System.err.printf("Warning: Invalid numeric values in profit file at line %d: %s\n", 
                                    lineNumber, line);
                }
            }
        }
        
        if (profits.isEmpty()) {
            throw new IOException("No valid profit values found in file: " + filename);
        }
        
        System.out.printf("Loaded %d item profits from %s\n", profits.size(), filename);
        return profits;
    }
    
    /**
     * Loads transaction database from file.
     * 
     * @param filename Database file path
     * @return List of transactions
     * @throws IOException if file cannot be read
     */
    private static List<Transaction> loadDatabase(String filename) throws IOException {
        List<Transaction> database = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int transactionId = 1;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }
                
                Map<Integer, Integer> items = new HashMap<>();
                Map<Integer, Double> probabilities = new HashMap<>();
                
                String[] entries = line.split("\\s+");
                boolean validTransaction = true;
                
                for (String entry : entries) {
                    String[] parts = entry.split(":");
                    if (parts.length != 3) {
                        System.err.printf("Warning: Invalid entry format at line %d: %s\n", 
                                        lineNumber, entry);
                        validTransaction = false;
                        break;
                    }
                    
                    try {
                        int itemId = Integer.parseInt(parts[0]);
                        int quantity = Integer.parseInt(parts[1]);
                        double probability = Double.parseDouble(parts[2]);
                        
                        if (quantity <= 0) {
                            System.err.printf("Warning: Non-positive quantity at line %d: %s\n", 
                                            lineNumber, entry);
                            continue;
                        }
                        
                        if (probability < 0.0 || probability > 1.0) {
                            System.err.printf("Warning: Invalid probability at line %d: %s\n", 
                                            lineNumber, entry);
                            continue;
                        }
                        
                        items.put(itemId, quantity);
                        probabilities.put(itemId, probability);
                        
                    } catch (NumberFormatException e) {
                        System.err.printf("Warning: Invalid numeric values at line %d: %s\n", 
                                        lineNumber, entry);
                        validTransaction = false;
                        break;
                    }
                }
                
                if (validTransaction && !items.isEmpty()) {
                    database.add(new Transaction(transactionId++, items, probabilities));
                }
            }
        }
        
        if (database.isEmpty()) {
            throw new IOException("No valid transactions found in file: " + filename);
        }
        
        System.out.printf("Loaded %d transactions from %s\n", database.size(), filename);
        return database;
    }
    
    /**
     * Exports mining results to files.
     * 
     * @param results Mining results
     * @param cmdArgs Command line arguments
     * @param engine Mining engine for statistics
     * @throws IOException if export fails
     */
    private static void exportResults(List<Itemset> results, CommandLineArgs cmdArgs, MiningEngine engine) 
            throws IOException {
        
        // Export main results
        String outputFile = cmdArgs.outputFile != null ? cmdArgs.outputFile : "ootkhuim_results.txt";
        exportMainResults(results, outputFile, cmdArgs);
        
        // Export statistics if requested
        if (cmdArgs.exportStatistics) {
            String statsFile = outputFile.replace(".txt", "_stats.txt");
            exportStatistics(engine, statsFile);
        }
        
        // Export detailed results if debug mode
        if (cmdArgs.debug) {
            String detailedFile = outputFile.replace(".txt", "_detailed.txt");
            exportDetailedResults(results, engine, detailedFile);
        }
    }
    
    /**
     * Exports main mining results.
     * 
     * @param results Mining results
     * @param filename Output filename
     * @param cmdArgs Command line arguments
     * @throws IOException if export fails
     */
    private static void exportMainResults(List<Itemset> results, String filename, CommandLineArgs cmdArgs) 
            throws IOException {
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header
            writer.printf("# %s Results\n", ALGORITHM_NAME);
            writer.printf("# Version: %s\n", VERSION);
            writer.printf("# Generated: %s\n", Instant.now().toString());
            writer.printf("# K: %d, MinProbability: %.6f\n", cmdArgs.k, cmdArgs.minProbability);
            writer.printf("# Total Results: %d\n", results.size());
            writer.println("#");
            writer.println("# Format: Rank | Items | Expected_Utility | Existential_Probability | Size");
            writer.println();
            
            // Results
            int rank = 1;
            for (Itemset itemset : results) {
                List<Integer> sortedItems = new ArrayList<>(itemset.getItems());
                Collections.sort(sortedItems);
                
                writer.printf("%d | %s | %.6f | %.6f | %d\n",
                             rank++,
                             sortedItems.toString().replaceAll("[\\[\\]]", ""),
                             itemset.getExpectedUtility(),
                             itemset.getExistentialProbability(),
                             itemset.size());
            }
        }
        
        System.out.printf("Results exported to: %s\n", filename);
    }
    
    /**
     * Exports mining statistics.
     * 
     * @param engine Mining engine
     * @param filename Statistics filename
     * @throws IOException if export fails
     */
    private static void exportStatistics(MiningEngine engine, String filename) throws IOException {
        MiningEngine.MiningResults detailedResults = engine.exportDetailedResults();
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.printf("# %s Statistics\n", ALGORITHM_NAME);
            writer.printf("# Version: %s\n", VERSION);
            writer.printf("# Generated: %s\n", Instant.now().toString());
            writer.println();
            
            writer.println("## Execution Statistics");
            writer.printf("Execution Time: %d ms\n", detailedResults.getExecutionTime());
            
            Map<String, Object> stats = detailedResults.getGeneralStatistics();
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                writer.printf("%s: %s\n", entry.getKey(), entry.getValue());
            }
            
            writer.println("\n## Pruning Statistics");
            writer.println(detailedResults.getPruningStats().toString());
            
            writer.println("\n## Top-K Manager Statistics");
            writer.println(detailedResults.getTopKStats().toString());
        }
        
        System.out.printf("Statistics exported to: %s\n", filename);
    }
    
    /**
     * Exports detailed results for debugging.
     * 
     * @param results Mining results
     * @param engine Mining engine
     * @param filename Detailed results filename
     * @throws IOException if export fails
     */
    private static void exportDetailedResults(List<Itemset> results, MiningEngine engine, String filename) 
            throws IOException {
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.printf("# %s Detailed Results\n", ALGORITHM_NAME);
            writer.printf("# Version: %s\n", VERSION);
            writer.printf("# Generated: %s\n", Instant.now().toString());
            writer.println();
            
            writer.println("## Top-K Itemsets (Detailed)");
            int rank = 1;
            for (Itemset itemset : results) {
                writer.printf("\n--- Rank %d ---\n", rank++);
                writer.println(itemset.toDetailedString());
            }
            
            writer.println("\n## Complete Mining Statistics");
            MiningEngine.MiningResults detailedResults = engine.exportDetailedResults();
            writer.println(detailedResults.toString());
        }
        
        System.out.printf("Detailed results exported to: %s\n", filename);
    }
    
    /**
     * Prints algorithm information.
     * 
     * @param cmdArgs Command line arguments
     */
    private static void printAlgorithmInfo(CommandLineArgs cmdArgs) {
        System.out.println("==========================================");
        System.out.printf(" %s - Version %s\n", ALGORITHM_NAME, VERSION);
        System.out.println("==========================================");
        System.out.println(" Object-Oriented Top-K High-Utility Itemset Mining");
        System.out.println(" from Uncertain Databases with Positive");
        System.out.println(" and Negative Utilities");
        System.out.println("==========================================");
        System.out.printf("Database File: %s\n", cmdArgs.databaseFile);
        System.out.printf("Profit File: %s\n", cmdArgs.profitFile);
        System.out.printf("K: %d\n", cmdArgs.k);
        System.out.printf("Min Probability: %.6f\n", cmdArgs.minProbability);
        
        if (cmdArgs.maxThreads > 0) {
            System.out.printf("Max Threads: %d\n", cmdArgs.maxThreads);
        }
        if (cmdArgs.disableObjectOriented) {
            System.out.println("Object-Oriented Processing: Disabled");
        }
        if (cmdArgs.verbose) {
            System.out.println("Verbose Mode: Enabled");
        }
        if (cmdArgs.debug) {
            System.out.println("Debug Mode: Enabled");
        }
        
        System.out.println("==========================================");
        System.out.println();
    }
    
    /**
     * Prints usage information and exits.
     */
    private static void printUsageAndExit() {
        System.out.printf("%s - Version %s\n\n", ALGORITHM_NAME, VERSION);
        System.out.println("Usage: java ver1_0 <database_file> <profit_file> <k> <min_probability> [options]");
        System.out.println();
        System.out.println("Required Arguments:");
        System.out.println("  database_file    Path to the transaction database file");
        System.out.println("  profit_file      Path to the item profit table file");
        System.out.println("  k                Number of top itemsets to find (positive integer)");
        System.out.println("  min_probability  Minimum existential probability threshold (0.0 to 1.0)");
        System.out.println();
        System.out.println("Optional Arguments:");
        System.out.println("  -o, --output <file>      Output file for results (default: ootkhuim_results.txt)");
        System.out.println("  -t, --threads <num>      Maximum number of threads for object-oriented processing");
        System.out.println("  -v, --verbose            Enable verbose logging");
        System.out.println("  -d, --debug              Enable debug mode with detailed output");
        System.out.println("  --no-object-oriented     Disable object-oriented processing");
        System.out.println("  -c, --config <file>      Load configuration from file");
        System.out.println("  --export-stats           Export detailed statistics");
        System.out.println("  -h, --help               Show this help message");
        System.out.println();
        System.out.println("File Formats:");
        System.out.println("  Database File: Each line contains items in format 'item:quantity:probability'");
        System.out.println("    Example: 1:2:0.8 3:1:0.6 5:3:0.9");
        System.out.println("  Profit File: Each line contains 'item_id profit_value'");
        System.out.println("    Example: 1 10.5");
        System.out.println("             2 -5.2");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java ver1_0 database.txt profits.txt 10 0.1");
        System.out.println("  java ver1_0 database.txt profits.txt 50 0.05 -v -t 8 -o results.txt");
        System.out.println("  java ver1_0 database.txt profits.txt 20 0.2 --debug --export-stats");
        
        System.exit(0);
    }
    
    /**
     * Checks if debug mode is enabled based on system properties.
     * 
     * @return True if debug mode is enabled
     */
    private static boolean isDebugMode() {
        return Boolean.parseBoolean(System.getProperty("debug", "false"));
    }
    
    /**
     * Validates input files exist and are readable.
     * 
     * @param databaseFile Database file path
     * @param profitFile Profit file path
     * @throws IOException if files don't exist or aren't readable
     */
    private static void validateInputFiles(String databaseFile, String profitFile) throws IOException {
        File dbFile = new File(databaseFile);
        File profFile = new File(profitFile);
        
        if (!dbFile.exists()) {
            throw new IOException("Database file not found: " + databaseFile);
        }
        
        if (!dbFile.canRead()) {
            throw new IOException("Cannot read database file: " + databaseFile);
        }
        
        if (!profFile.exists()) {
            throw new IOException("Profit file not found: " + profitFile);
        }
        
        if (!profFile.canRead()) {
            throw new IOException("Cannot read profit file: " + profitFile);
        }
        
        System.out.println("Input files validated successfully.");
    }
    
    /**
     * Command line arguments container.
     */
    private static class CommandLineArgs {
        String databaseFile;
        String profitFile;
        int k;
        double minProbability;
        String outputFile;
        String configFile;
        int maxThreads = 0;
        boolean verbose = false;
        boolean debug = false;
        boolean disableObjectOriented = false;
        boolean exportStatistics = false;
        
        @Override
        public String toString() {
            return String.format("CommandLineArgs{db=%s, profit=%s, k=%d, minProb=%.6f, " +
                               "output=%s, config=%s, threads=%d, verbose=%b, debug=%b, " +
                               "noObjectOriented=%b, exportStats=%b}",
                               databaseFile, profitFile, k, minProbability,
                               outputFile, configFile, maxThreads, verbose, debug,
                               disableObjectOriented, exportStatistics);
        }
    }
    
    /**
     * Performs basic validation of command line arguments.
     * 
     * @param cmdArgs Command line arguments
     * @throws IllegalArgumentException if arguments are invalid
     */
    private static void validateCommandLineArgs(CommandLineArgs cmdArgs) throws IOException {
        if (cmdArgs.k <= 0) {
            throw new IllegalArgumentException("K must be a positive integer");
        }
        
        if (cmdArgs.minProbability < 0.0 || cmdArgs.minProbability > 1.0) {
            throw new IllegalArgumentException("Minimum probability must be between 0.0 and 1.0");
        }
        
        if (cmdArgs.maxThreads < 0) {
            throw new IllegalArgumentException("Max threads cannot be negative");
        }
        
        // Validate input files
        validateInputFiles(cmdArgs.databaseFile, cmdArgs.profitFile);
        
        // Validate output directory
        if (cmdArgs.outputFile != null) {
            File outputFile = new File(cmdArgs.outputFile);
            File outputDir = outputFile.getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                if (!outputDir.mkdirs()) {
                    throw new IOException("Cannot create output directory: " + outputDir.getAbsolutePath());
                }
            }
        }
        
        // Validate config file if specified
        if (cmdArgs.configFile != null) {
            File configFile = new File(cmdArgs.configFile);
            if (!configFile.exists()) {
                throw new IOException("Configuration file not found: " + cmdArgs.configFile);
            }
            if (!configFile.canRead()) {
                throw new IOException("Cannot read configuration file: " + cmdArgs.configFile);
            }
        }
        
        System.out.println("Command line arguments validated successfully.");
    }
    
    /**
     * Creates a sample configuration file for reference.
     * 
     * @param filename Configuration file path
     * @throws IOException if file cannot be created
     */
    public static void createSampleConfigFile(String filename) throws IOException {
        Configuration sampleConfig = new Configuration();
        sampleConfig.saveToFile(filename);
        System.out.printf("Sample configuration file created: %s\n", filename);
    }
    
    /**
     * Runs performance benchmark with sample data.
     * 
     * @param args Benchmark arguments
     */
    public static void runBenchmark(String[] args) {
        System.out.println("Running performance benchmark...");
        
        // TODO: Implement benchmark functionality
        // This could include:
        // - Generating synthetic datasets
        // - Running multiple test cases
        // - Measuring performance metrics
        // - Comparing with baseline algorithms
        
        System.out.println("Benchmark functionality not implemented yet.");
    }
    
    /**
     * Validates the mining results for correctness.
     * 
     * @param results Mining results
     * @param database Original database
     * @param itemProfits Item profits
     * @param k Expected K value
     * @param minProbability Minimum probability threshold
     * @return True if results are valid
     */
    public static boolean validateResults(List<Itemset> results, 
                                        List<Transaction> database,
                                        Map<Integer, Double> itemProfits,
                                        int k, 
                                        double minProbability) {
        
        if (results.size() > k) {
            System.err.printf("Error: Results contain %d itemsets, expected at most %d\n", 
                             results.size(), k);
            return false;
        }
        
        double previousUtility = Double.MAX_VALUE;
        for (int i = 0; i < results.size(); i++) {
            Itemset itemset = results.get(i);
            
            // Check utility ordering (descending)
            if (itemset.getExpectedUtility() > previousUtility + 1e-10) {
                System.err.printf("Error: Results not sorted by utility at position %d\n", i);
                return false;
            }
            previousUtility = itemset.getExpectedUtility();
            
            // Check minimum probability constraint
            if (itemset.getExistentialProbability() < minProbability - 1e-10) {
                System.err.printf("Error: Itemset at position %d has probability %.6f < %.6f\n", 
                                 i, itemset.getExistentialProbability(), minProbability);
                return false;
            }
            
            // Check itemset is not empty
            if (itemset.isEmpty()) {
                System.err.printf("Error: Empty itemset found at position %d\n", i);
                return false;
            }
        }
        
        System.out.println("Results validation completed successfully.");
        return true;
    }
}