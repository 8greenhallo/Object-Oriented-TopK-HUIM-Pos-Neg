package ootkhuimunpnu.vntm;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.config.ExperimentConfig;
import ootkhuimunpnu.vntm.config.SearchConfig;
import ootkhuimunpnu.vntm.db.Itemset;
import ootkhuimunpnu.vntm.experiment.ExperimentRunner;
import ootkhuimunpnu.vntm.io.ProfitTableReader;

/**
 * Main entry point for the OOTK-HUIM-UB± algorithm.
 *
 * <h3>Usage</h3>
 * <pre>
 *   java App &lt;database_file&gt; &lt;profit_file&gt; &lt;k&gt; &lt;min_probability&gt;
 * </pre>
 *
 * <h3>Example</h3>
 * <pre>
 *   java App resources/datasets/db.txt resources/datasets/profits.txt 100 0.1
 * </pre>
 *
 * <h3>Configuration</h3>
 * All strategy selections (search algorithm, top-K manager, pruning variant)
 * are made here before passing to the engine.  To switch strategies, change
 * the constructor argument (e.g., swap {@code DepthFirstSearch} for
 * {@code BreadthFirstSearch}).
 *
 * @author Meg
 * @version 5.0
 */
public class App {

    /**
     * Application entry point.
     *
     * @param args command-line arguments: database_file profit_file k min_probability
     */
    public static void main(String[] args) {
        if (args.length != 4) {
            printUsage();
            System.exit(1);
        }

        String databaseFile  = args[0];
        String profitFile    = args[1];
        int    k             = Integer.parseInt(args[2]);
        double minProb       = Double.parseDouble(args[3]);

        System.out.println("=".repeat(60));
        System.out.println("  OOTK-HUIM-UB±: Probabilistic High-Utility Itemset Mining");
        System.out.println("=".repeat(60));

        try {
            // ---------------------------------------------------------------
            // 1. Load profit table (needed to build AlgorithmConfig)
            // ---------------------------------------------------------------
            ProfitTableReader profitReader = new ProfitTableReader();
            Map<Integer, Double> profits   = profitReader.read(profitFile);

            // ---------------------------------------------------------------
            // 2. Build AlgorithmConfig (K, minProbability, profit table)
            // ---------------------------------------------------------------
            AlgorithmConfig algorithmConfig = new AlgorithmConfig.Builder()
                    .setK(k)
                    .setMinProbability(minProb)
                    .setProfitTable(profits)
                    .setVerbose(true)
                    .setMemoryCheckInterval(10)
                    .setProgressReportInterval(10)
                    .build();

            // ---------------------------------------------------------------
            // 3. Build SearchConfig (DFS, unlimited depth, dynamic threshold)
            // ---------------------------------------------------------------
            SearchConfig searchConfig = new SearchConfig.Builder()
                    //.setStrategyType(SearchConfig.StrategyType.DFS)
                    .setStrategyType(SearchConfig.StrategyType.BFS)
                    //.setStrategyType(SearchConfig.StrategyType.BEST_FIRST)
                    .setDynamicThreshold(true)
                    .build();

            // ---------------------------------------------------------------
            // 4. Build ExperimentConfig (paths, output directory, 1 repetition)
            // ---------------------------------------------------------------
            ExperimentConfig experimentConfig = new ExperimentConfig.Builder()
                    .setDatabasePath(databaseFile)
                    .setProfitTablePath(profitFile)
                    .setOutputDirectory("./output")
                    .setExperimentName("ootk_huim_ub_pm_k=" + k + "_p=" + minProb)
                    .setRepetitions(3)
                    .build();

            // ---------------------------------------------------------------
            // 5. Run experiment
            // ---------------------------------------------------------------
            ExperimentRunner runner = new ExperimentRunner(
                    experimentConfig, algorithmConfig, searchConfig);

            List<Itemset> results = runner.run();

            System.out.println("\n" + "=".repeat(60));
            System.out.println("  Mining Completed Successfully");
            System.out.println("  Results saved to: ./output/");
            System.out.println("=".repeat(60));

        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("Usage: java App <database_file> <profit_file> <k> <min_probability>");
        System.err.println();
        System.err.println("Arguments:");
        System.err.println("  database_file   : Transaction database (item:qty:prob per line)");
        System.err.println("  profit_file     : Item profits (item_id profit per line)");
        System.err.println("  k               : Number of top itemsets (positive integer)");
        System.err.println("  min_probability : Minimum EP threshold [0.0 - 1.0]");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java App database.txt profits.txt 100 0.1");
    }
}