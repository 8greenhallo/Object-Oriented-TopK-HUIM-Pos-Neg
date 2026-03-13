package ootkhuimunpnu.vntm.experiment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.config.ExperimentConfig;
import ootkhuimunpnu.vntm.config.SearchConfig;
import ootkhuimunpnu.vntm.db.Itemset;

/**
 * Executes a grid of experiments across multiple (K, minProbability) combinations.
 *
 * <p>Iterates over all (K, minProbability) pairs specified in
 * {@link ExperimentConfig#getKValues()} and
 * {@link ExperimentConfig#getMinProbabilityValues()}, running an
 * {@link ExperimentRunner} for each combination.
 *
 * <p>Results are collected and a summary comparison table is printed
 * showing how execution time and pruning effectiveness vary across parameters.
 *
 * @author Meg
 * @version 5.0
 */
public class BatchExperimentRunner {

    private final ExperimentConfig baseExperimentConfig;
    private final AlgorithmConfig  baseAlgorithmConfig;
    private final SearchConfig     searchConfig;

    /**
     * Constructs a batch runner.
     *
     * @param baseExperimentConfig experiment settings (including K and minP value lists)
     * @param baseAlgorithmConfig  base algorithm config (K and minP will be overridden)
     * @param searchConfig         search strategy settings (shared across all runs)
     */
    public BatchExperimentRunner(ExperimentConfig baseExperimentConfig,
                                  AlgorithmConfig  baseAlgorithmConfig,
                                  SearchConfig     searchConfig) {
        this.baseExperimentConfig = baseExperimentConfig;
        this.baseAlgorithmConfig  = baseAlgorithmConfig;
        this.searchConfig         = searchConfig;
    }

    /**
     * Runs all experiments in the grid and returns all result sets.
     *
     * @return list of result sets, one per (K, minP) combination
     */
    public List<List<Itemset>> runAll() {
        List<Integer> kValues = baseExperimentConfig.getKValues();
        List<Double>  pValues = baseExperimentConfig.getMinProbabilityValues();

        // If sweep lists are empty, fall back to single run using base config
        if (kValues.isEmpty())    kValues = List.of(baseAlgorithmConfig.getK());
        if (pValues.isEmpty())    pValues = List.of(baseAlgorithmConfig.getMinProbability());

        List<List<Itemset>> allResults = new ArrayList<>();
        int total = kValues.size() * pValues.size();
        int run   = 0;

        System.out.println("=== Batch Experiment Runner ===");
        System.out.printf("Grid: %d K-values × %d minP-values = %d runs%n",
                kValues.size(), pValues.size(), total);

        for (int k : kValues) {
            for (double minP : pValues) {
                run++;
                System.out.printf("%n[%d/%d] K=%d, minP=%.3f%n", run, total, k, minP);

                try {
                    // Build per-run algorithm config with swept parameters
                    AlgorithmConfig runConfig = new AlgorithmConfig.Builder()
                            .setK(k)
                            .setMinProbability(minP)
                            .setProfitTable(baseAlgorithmConfig.getProfitTable())
                            .setVerbose(baseAlgorithmConfig.isVerbose())
                            .setMemoryCheckInterval(baseAlgorithmConfig.getMemoryCheckInterval())
                            .setProgressReportInterval(baseAlgorithmConfig.getProgressReportInterval())
                            .build();

                    // Build per-run experiment config with a unique name
                    ExperimentConfig runExpConfig = new ExperimentConfig.Builder()
                            .setDatabasePath(baseExperimentConfig.getDatabasePath())
                            .setProfitTablePath(baseExperimentConfig.getProfitTablePath())
                            .setOutputDirectory(baseExperimentConfig.getOutputDirectory())
                            .setRepetitions(baseExperimentConfig.getRepetitions())
                            .setExperimentName(baseExperimentConfig.getExperimentName()
                                    + "_k" + k + "_p" + String.format("%.2f", minP))
                            .build();

                    ExperimentRunner runner = new ExperimentRunner(
                            runExpConfig, runConfig, searchConfig);
                    List<Itemset> results = runner.run();
                    allResults.add(results);

                } catch (IOException e) {
                    System.err.printf("Run %d failed: %s%n", run, e.getMessage());
                    allResults.add(new ArrayList<>());
                }
            }
        }

        System.out.println("\n=== Batch Complete: " + run + " runs finished ===");
        return allResults;
    }
}