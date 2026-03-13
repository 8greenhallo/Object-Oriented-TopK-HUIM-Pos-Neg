package ootkhuimunpnu.vntm.monitoring;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import ootkhuimunpnu.vntm.db.Itemset;

/**
 * Generates research-quality reports in CSV and plain-text formats.
 *
 * <p>The primary output is a CPU-time breakdown (pie chart data) showing
 * where the algorithm spends time across the five strategy components.
 * This directly answers the research question: which component dominates
 * execution time and should be optimised first?
 *
 * <h3>Output Files</h3>
 * <ul>
 *   <li>{@code <name>_profile.csv} — component exclusive times for spreadsheet import</li>
 *   <li>{@code <name>_results.csv} — top-K itemsets with EU and EP</li>
 *   <li>{@code <name>_summary.txt} — human-readable summary</li>
 * </ul>
 *
 * @author Meg
 * @version 5.0
 */
public class ReportGenerator {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String outputDirectory;
    private final String experimentName;

    /**
     * @param outputDirectory directory where files will be written
     * @param experimentName  prefix for output file names
     */
    public ReportGenerator(String outputDirectory, String experimentName) {
        this.outputDirectory = outputDirectory;
        this.experimentName  = experimentName;
    }

    /**
     * Writes all report files.
     *
     * @param profiler   performance profiler with exclusive times
     * @param stats      statistics collector
     * @param topK       top-K results
     * @param k          number of top itemsets requested
     * @param threshold  final mining threshold
     */
    public void generate(PerformanceProfiler profiler,
                         StatisticsCollector stats,
                         List<Itemset> topK,
                         int k,
                         double threshold) {
        try {
            Files.createDirectories(Paths.get(outputDirectory));
            String ts = LocalDateTime.now().format(TIMESTAMP_FMT);
            String base = outputDirectory + "/" + experimentName + "_" + ts;

            writeProfileCsv(base + "_profile.csv", profiler);
            writeResultsCsv(base + "_results.csv", topK);
            writeSummary(base + "_summary.txt", profiler, stats, topK, k, threshold);

            System.out.println("Reports written to: " + outputDirectory);
        } catch (IOException e) {
            System.err.println("Failed to write reports: " + e.getMessage());
        }
    }

    // =========================================================================
    // Private writers
    // =========================================================================

    private void writeProfileCsv(String path, PerformanceProfiler profiler) throws IOException {
        try (PrintWriter w = new PrintWriter(path)) {
            w.println("component,exclusive_time_ms,percentage");
            for (PerformanceProfiler.Component c : PerformanceProfiler.Component.values()) {
                w.printf("%s,%.4f,%.4f%n",
                        c.name(),
                        profiler.getExclusiveTimeMs(c),
                        profiler.getPercentage(c));
            }
        }
    }

    private void writeResultsCsv(String path, List<Itemset> topK) throws IOException {
        try (PrintWriter w = new PrintWriter(path)) {
            w.println("rank,items,expected_utility,existential_probability,size");
            int rank = 1;
            for (Itemset is : topK) {
                w.printf("%d,\"%s\",%.6f,%.6f,%d%n",
                        rank++,
                        is.getItems().toString(),
                        is.getExpectedUtility(),
                        is.getExistentialProbability(),
                        is.size());
            }
        }
    }

    private void writeSummary(String path, PerformanceProfiler profiler,
                               StatisticsCollector stats, List<Itemset> topK,
                               int k, double threshold) throws IOException {
        try (PrintWriter w = new PrintWriter(path)) {
            w.println("=== OOTK-PHUIM Experiment Summary ===");
            w.println("Experiment: " + experimentName);
            w.println("Generated: " + LocalDateTime.now());
            w.println();
            w.println("--- Parameters ---");
            w.println("K                : " + k);
            w.println("Final threshold  : " + String.format("%.4f", threshold));
            w.println("Results found    : " + topK.size());
            w.println();
            w.println("--- Performance Profile (Exclusive Time) ---");
            w.printf("%-20s | %-12s | %-8s%n", "Component", "Time (ms)", "%");
            w.println("-".repeat(50));
            for (PerformanceProfiler.Component c : PerformanceProfiler.Component.values()) {
                w.printf("%-20s | %12.2f | %6.2f%%%n",
                        c.name(), profiler.getExclusiveTimeMs(c), profiler.getPercentage(c));
            }
            w.printf("%-20s | %12.2f | %6s%n", "TOTAL", profiler.getTotalTimeMs(), "100.00%");
            w.println();
            w.println("--- Pruning Statistics ---");
            w.println("Candidates generated  : " + stats.getCandidatesGenerated());
            w.println("Total pruned          : " + stats.getCandidatesPruned());
            w.println("  PTWU pruned         : " + stats.getPtwuPruned());
            w.println("  Branch pruned       : " + stats.getBranchPruned());
            w.println("  Bulk pruned         : " + stats.getBulkBranchPruned());
            w.println("  EU+R pruned         : " + stats.getEuPruned());
            w.println("  EP pruned           : " + stats.getEpPruned());
            w.printf ("Peak memory           : %.2f MB%n", stats.getPeakMemoryMb());
            w.println();
            w.println("--- Top-K Results ---");
            int rank = 1;
            for (Itemset is : topK) {
                w.println(is.toOutputString(rank++));
            }
        }
    }
}