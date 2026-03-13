package ootkhuimunpnu.vntm.monitoring;

/**
 * Immutable snapshot of pruning statistics at a point in time.
 *
 * <p>Extracted from {@link StatisticsCollector} at the end of a run to
 * produce a serialisable record for CSV/JSON output.
 *
 * @author Meg
 * @version 5.0
 */
public class PruningStatistics {

    public final long ptwuPruned;
    public final long branchPruned;
    public final long bulkBranchPruned;
    public final long euPruned;
    public final long epPruned;
    public final long totalPruned;
    public final long candidatesGenerated;

    /**
     * Constructs a snapshot from a live {@link StatisticsCollector}.
     *
     * @param stats live collector
     */
    public PruningStatistics(StatisticsCollector stats) {
        this.ptwuPruned        = stats.getPtwuPruned();
        this.branchPruned      = stats.getBranchPruned();
        this.bulkBranchPruned  = stats.getBulkBranchPruned();
        this.euPruned          = stats.getEuPruned();
        this.epPruned          = stats.getEpPruned();
        this.totalPruned       = stats.getCandidatesPruned();
        this.candidatesGenerated = stats.getCandidatesGenerated();
    }

    /**
     * Prints a formatted pruning report.
     */
    public void printReport() {
        System.out.println("\n=== Pruning Effectiveness ===");
        System.out.println("Candidates generated : " + candidatesGenerated);
        System.out.println("Total pruned         : " + totalPruned);
        if (candidatesGenerated > 0) {
            double rate = 100.0 * totalPruned / (totalPruned + candidatesGenerated);
            System.out.printf("Pruning rate         : %.1f%%%n", rate);
        }
        System.out.println("  PTWU pruned        : " + ptwuPruned);
        System.out.println("  Branch pruned      : " + branchPruned);
        System.out.println("  Bulk branch pruned : " + bulkBranchPruned);
        System.out.println("  EU+R pruned        : " + euPruned);
        System.out.println("  EP pruned          : " + epPruned);
    }
}