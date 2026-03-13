package ootkhuimunpnu.vntm.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for a single experiment or a batch of experiments.
 *
 * <p>Combines dataset paths, repetition counts, and output settings.
 * Used by {@link experiment.ExperimentRunner} and
 * {@link experiment.BatchExperimentRunner} to orchestrate runs.
 *
 * @author Meg
 * @version 5.0
 */
public class ExperimentConfig {

    /** Path to the transaction database file. */
    private final String databasePath;

    /** Path to the item profit table file. */
    private final String profitTablePath;

    /** Directory where result files (CSV, JSON) will be written. */
    private final String outputDirectory;

    /**
     * Number of repetitions for each parameter combination.
     * Results are averaged over repetitions to reduce timing variance.
     */
    private final int repetitions;

    /**
     * Whether to write per-run result files.
     * When {@code false} only the aggregated summary is written.
     */
    private final boolean writePerRunResults;

    /**
     * Human-readable name for this experiment (used in result filenames).
     */
    private final String experimentName;

    /**
     * Optional list of K values to sweep over for batch experiments.
     * If empty, the K from {@link AlgorithmConfig} is used directly.
     */
    private final List<Integer> kValues;

    /**
     * Optional list of minProbability values to sweep over for batch experiments.
     */
    private final List<Double> minProbabilityValues;

    private ExperimentConfig(Builder b) {
        this.databasePath        = b.databasePath;
        this.profitTablePath     = b.profitTablePath;
        this.outputDirectory     = b.outputDirectory;
        this.repetitions         = b.repetitions;
        this.writePerRunResults  = b.writePerRunResults;
        this.experimentName      = b.experimentName;
        this.kValues             = Collections.unmodifiableList(new ArrayList<>(b.kValues));
        this.minProbabilityValues = Collections.unmodifiableList(new ArrayList<>(b.minProbabilityValues));
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return database file path */
    public String getDatabasePath() { return databasePath; }

    /** @return profit table file path */
    public String getProfitTablePath() { return profitTablePath; }

    /** @return output directory path */
    public String getOutputDirectory() { return outputDirectory; }

    /** @return number of repetitions per parameter set */
    public int getRepetitions() { return repetitions; }

    /** @return whether per-run result files are written */
    public boolean isWritePerRunResults() { return writePerRunResults; }

    /** @return experiment name */
    public String getExperimentName() { return experimentName; }

    /** @return list of K values to sweep (empty = single run) */
    public List<Integer> getKValues() { return kValues; }

    /** @return list of minProbability values to sweep (empty = single run) */
    public List<Double> getMinProbabilityValues() { return minProbabilityValues; }

    @Override
    public String toString() {
        return String.format("ExperimentConfig{name='%s', db='%s', reps=%d}",
                experimentName, databasePath, repetitions);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Fluent builder for {@link ExperimentConfig}. */
    public static class Builder {

        private String databasePath = "";
        private String profitTablePath = "";
        private String outputDirectory = "./output";
        private int repetitions = 1;
        private boolean writePerRunResults = false;
        private String experimentName = "experiment";
        private List<Integer> kValues = new ArrayList<>();
        private List<Double> minProbabilityValues = new ArrayList<>();

        public Builder setDatabasePath(String path) { this.databasePath = path; return this; }
        public Builder setProfitTablePath(String path) { this.profitTablePath = path; return this; }
        public Builder setOutputDirectory(String dir) { this.outputDirectory = dir; return this; }
        public Builder setRepetitions(int n) { this.repetitions = n; return this; }
        public Builder setWritePerRunResults(boolean b) { this.writePerRunResults = b; return this; }
        public Builder setExperimentName(String name) { this.experimentName = name; return this; }
        public Builder addKValue(int k) { this.kValues.add(k); return this; }
        public Builder addMinProbabilityValue(double p) { this.minProbabilityValues.add(p); return this; }

        /** @return built {@link ExperimentConfig} */
        public ExperimentConfig build() {
            if (databasePath.isEmpty())
                throw new IllegalArgumentException("databasePath must not be empty");
            if (profitTablePath.isEmpty())
                throw new IllegalArgumentException("profitTablePath must not be empty");
            return new ExperimentConfig(this);
        }
    }
}