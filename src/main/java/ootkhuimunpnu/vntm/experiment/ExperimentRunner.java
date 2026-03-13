package ootkhuimunpnu.vntm.experiment;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import ootkhuimunpnu.vntm.config.AlgorithmConfig;
import ootkhuimunpnu.vntm.config.ExperimentConfig;
import ootkhuimunpnu.vntm.config.SearchConfig;
import ootkhuimunpnu.vntm.db.Itemset;
import ootkhuimunpnu.vntm.db.RawTransaction;
import ootkhuimunpnu.vntm.io.DataValidator;
import ootkhuimunpnu.vntm.io.ProfitTableReader;
import ootkhuimunpnu.vntm.io.ResultWriter;
import ootkhuimunpnu.vntm.io.TransactionFileReader;
import ootkhuimunpnu.vntm.mining.IMiningEngine;
import ootkhuimunpnu.vntm.mining.SequentialMiningEngine;
import ootkhuimunpnu.vntm.monitoring.PerformanceProfiler;
import ootkhuimunpnu.vntm.monitoring.ReportGenerator;
import ootkhuimunpnu.vntm.monitoring.StatisticsCollector;
import ootkhuimunpnu.vntm.pruning.CompositePruningStrategy;
import ootkhuimunpnu.vntm.join.*;
import ootkhuimunpnu.vntm.search.*;
import ootkhuimunpnu.vntm.topk.*;

/**
 * Orchestrates a single experiment run.
 *
 * <p>Loads data, constructs the engine with the specified strategies,
 * executes mining, and outputs reports.
 *
 * <h3>Wire-up order</h3>
 * <ol>
 *   <li>Load data (profit table + database)</li>
 *   <li>Validate data</li>
 *   <li>Build {@link AlgorithmConfig}</li>
 *   <li>Instantiate strategies (TopK, Pruning, Join, Search)</li>
 *   <li>Instantiate {@link SequentialMiningEngine}</li>
 *   <li>Run {@link IMiningEngine#mine}</li>
 *   <li>Write experiment reports</li>
 * </ol>
 *
 * @author Meg
 * @version 5.0
 */
public class ExperimentRunner {

    private final ExperimentConfig experimentConfig;
    private final AlgorithmConfig  algorithmConfig;
    private final SearchConfig     searchConfig;

    /**
     * Constructs an ExperimentRunner.
     *
     * @param experimentConfig dataset and output settings
     * @param algorithmConfig  K, minProbability, profit table
     * @param searchConfig     search strategy settings
     */
    public ExperimentRunner(ExperimentConfig experimentConfig,
                             AlgorithmConfig  algorithmConfig,
                             SearchConfig     searchConfig) {
        this.experimentConfig = experimentConfig;
        this.algorithmConfig  = algorithmConfig;
        this.searchConfig     = searchConfig;
    }

    /**
     * Executes the experiment.
     *
     * @return top-K itemsets found
     * @throws IOException if data files cannot be read
     */
    public List<Itemset> run() throws IOException {

        // =====================================================================
        // 1. Load data
        // =====================================================================
        ProfitTableReader profitReader = new ProfitTableReader();
        Map<Integer, Double> profits = profitReader.read(experimentConfig.getProfitTablePath());

        TransactionFileReader txReader = new TransactionFileReader();
        List<RawTransaction> database = txReader.read(experimentConfig.getDatabasePath());

        // =====================================================================
        // 2. Validate data
        // =====================================================================
        DataValidator validator = new DataValidator();
        validator.validateDatabase(database);
        validator.validateProfitTable(profits);

        // =====================================================================
        // 3. Build algorithm config (merge profit table if not already set)
        // =====================================================================
        // The AlgorithmConfig may already have the profit table; if not, rebuild.
        AlgorithmConfig cfg = (algorithmConfig.getProfitTable().isEmpty())
            ? new AlgorithmConfig.Builder()
                .setK(algorithmConfig.getK())
                .setMinProbability(algorithmConfig.getMinProbability())
                .setProfitTable(profits)
                .setVerbose(algorithmConfig.isVerbose())
                .build()
            : algorithmConfig;

        // =====================================================================
        // 4. Instantiate strategies
        // =====================================================================
        PerformanceProfiler profiler = new PerformanceProfiler(searchConfig);
        //PerformanceProfiler profiler = new PerformanceProfiler();
        StatisticsCollector stats    = new StatisticsCollector();

        // ===========Top-K manager selection point===========
        //DualTopKManager topK = new DualTopKManager(cfg.getK());
        ITopKManager topK = new FTKTopKManager(cfg.getK());  // alternative: FTKHUIM-inspired manager with fast threshold-raising
        //ITopKManager topK = new TopHUITopKManager(cfg.getK());  // alternative: TopHUI-inspired manager with multi-phase threshold raising

        CompositePruningStrategy pruning = new CompositePruningStrategy(cfg.getMinProbability());
        
        // ===========Join strategy selection point===========
        // Inject via IJoinStrategy = swap to another joining strategies here
        // to benchmark different join variants.
        //SortedMergeJoin joinStrategy = new SortedMergeJoin();      // baseline: standard tidset join
        IJoinStrategy joinStrategy = new DiffsetJoin();      // alternative: diffset join
        //IJoinStrategy joinStrategy = new BufferedJoin();     // alternative: buffered join with early termination heuristic

        // ===========Search strategy selection point===========
        //DepthFirstSearch search = new DepthFirstSearch(joinStrategy);
        //ISearchStrategy search = new BestFirstSearch(joinStrategy);      // alternative: best-first search with probabilistic max-potential heuristic
        //ISearchStrategy search = new BreadthFirstSearch(joinStrategy);  // alternative: breadth-first search (for completeness, not expected to perform well)
        
        // Initialize dynamic search strategy via factory
        ISearchStrategy search = SearchStrategyFactory.createStrategy(searchConfig, joinStrategy);

        // =====================================================================
        // 5. Build mining engine
        // =====================================================================
        SequentialMiningEngine engine = new SequentialMiningEngine(
                cfg, topK, pruning, search, profiler, stats);

        // =====================================================================
        // 6. Run experiment (with repetitions)
        // =====================================================================
        List<Itemset> results = null;
        int reps = Math.max(1, experimentConfig.getRepetitions());

        for (int rep = 0; rep < reps; rep++) {
            if (reps > 1) System.out.printf("%n=== Repetition %d/%d ===%n", rep + 1, reps);

            // Reset accumulators for each repetition (except last one we keep for report)
            profiler.reset();
            stats.reset();

            results = engine.mine(database);
        }

        // =====================================================================
        // 7. Write reports
        // =====================================================================
        ReportGenerator reporter = new ReportGenerator(
                experimentConfig.getOutputDirectory(),
                experimentConfig.getExperimentName());
        reporter.generate(profiler, stats, results,
                          cfg.getK(), topK.getThreshold());

        ResultWriter writer = new ResultWriter(experimentConfig.getOutputDirectory());
        writer.printToConsole(results, cfg.getK());

        return results;
    }
}