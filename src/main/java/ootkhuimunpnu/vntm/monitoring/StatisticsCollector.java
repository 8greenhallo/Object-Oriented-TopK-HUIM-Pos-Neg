package ootkhuimunpnu.vntm.monitoring;

/**
 * Thread-safe collector for algorithm execution statistics.
 *
 * <p>Tracks candidate generation, pruning counts, memory usage, and
 * utility list creation.
 *
 * <p>Statistics are aggregated here rather than in individual classes
 * so that a single report can be generated at the end of mining without
 * coordinating multiple objects.
 *
 * @author Meg
 * @version 5.0
 */
public class StatisticsCollector {

    // =========================================================================
    // Candidate tracking
    // =========================================================================

    /** Number of itemsets generated (passed join but before pruning). */
    private long candidatesGenerated = 0;

    /** Number of itemsets pruned (by any pruning condition). */
    private long candidatesPruned    = 0;

    /** Number of PULList objects created (including intermediate). */
    private long utilityListsCreated = 0;

    // =========================================================================
    // Pruning breakdown
    // =========================================================================

    /** Candidates pruned by PTWU condition. */
    private long ptwuPruned          = 0;

    /** Candidates pruned by item-level branch pruning. */
    private long branchPruned        = 0;

    /** Candidates pruned by bulk branch pruning (whole batch). */
    private long bulkBranchPruned    = 0;

    /** Candidates pruned by EU + remaining condition. */
    private long euPruned            = 0;

    /** Candidates pruned by existential probability condition. */
    private long epPruned            = 0;
    // =========================================================================
    // Memory
    // =========================================================================

    /** Peak JVM heap usage observed during mining (bytes). */
    private long peakMemoryBytes     = 0;

    // =========================================================================
    // Increment methods (called from search / mining code)
    // =========================================================================

    public void incrementCandidatesGenerated() { this.candidatesGenerated++; }
    public void incrementCandidatesPruned()    { this.candidatesPruned++; }
    public void incrementUtilityListsCreated() { this.utilityListsCreated++; }
    public void incrementPtwuPruned()          { this.ptwuPruned++; }
    public void incrementBranchPruned()        { this.branchPruned++; }
    public void incrementBulkBranchPruned()    { this.bulkBranchPruned++; }
    public void incrementEuPruned()            { this.euPruned++; }
    public void incrementEpPruned()            { this.epPruned++; }

    /**
     * Updates the peak memory watermark if {@code usedBytes} exceeds the current peak.
     *
     * @param usedBytes current JVM heap usage in bytes
     */
    public void updatePeakMemory(long usedBytes) {
        if (usedBytes > peakMemoryBytes) {
            this.peakMemoryBytes = usedBytes;
        }
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public long getCandidatesGenerated() { return candidatesGenerated; }
    public long getCandidatesPruned()    { return candidatesPruned; }
    public long getUtilityListsCreated() { return utilityListsCreated; }
    public long getPtwuPruned()          { return ptwuPruned; }
    public long getBranchPruned()        { return branchPruned; }
    public long getBulkBranchPruned()   { return bulkBranchPruned; }
    public long getEuPruned()            { return euPruned; }
    public long getEpPruned()            { return epPruned; }
    public long getPeakMemoryBytes()     { return peakMemoryBytes; }

    /** @return peak memory usage in MB */
    public double getPeakMemoryMb()     { return peakMemoryBytes / (1024.0 * 1024.0); }

    // =========================================================================
    // Reporting
    // =========================================================================

    /**
     * Prints a formatted statistics report to stdout.
     */
    public void printReport() {
        long totalPruned = candidatesPruned;
        System.out.println("\n=== Algorithm Statistics ===");
        System.out.println("Candidates generated : " + candidatesGenerated);
        System.out.println("Utility lists created: " + utilityListsCreated);
        System.out.println("Total pruned         : " + totalPruned);
        System.out.println("  - PTWU pruned      : " + ptwuPruned);
        System.out.println("  - Branch pruned    : " + branchPruned);
        System.out.println("  - Bulk pruned      : " + bulkBranchPruned);
        System.out.println("  - EU+R pruned      : " + euPruned);
        System.out.println("  - EP pruned        : " + epPruned);
        System.out.printf ("Peak memory usage    : %.2f MB%n", getPeakMemoryMb());
    }

    /**
     * Resets all counters to zero.
     * Call between experiment repetitions.
     */
    public void reset() {
        candidatesGenerated = 0;
        candidatesPruned = 0;
        utilityListsCreated = 0;
        ptwuPruned = 0;
        branchPruned = 0;
        bulkBranchPruned = 0;
        euPruned = 0;
        epPruned = 0;
        peakMemoryBytes = 0;
    }
}