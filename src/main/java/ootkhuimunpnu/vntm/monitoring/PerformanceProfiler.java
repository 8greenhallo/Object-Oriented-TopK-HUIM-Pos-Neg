package ootkhuimunpnu.vntm.monitoring;

import ootkhuimunpnu.vntm.config.SearchConfig;
import java.util.EnumMap;
import java.util.Map;

/**
 * Exclusive-time performance profiler for the 5 HUIM algorithm components.
 *
 * <h3>What is Exclusive Time?</h3>
 * <p>When component A calls component B, normal (inclusive) timing counts
 * the time spent inside B towards A's total. Exclusive timing does not:
 * only the wall-clock time the CPU spends exclusively inside A is counted.
 *
 * <p>Example:
 * <pre>
 *   Search calls Join:
 *     - Search timer starts
 *     - [some search overhead] ...
 *     - Search timer PAUSES → Join timer STARTS
 *     - [join computation]
 *     - Join timer STOPS → Search timer RESUMES
 *   Result: Join exclusive time = join duration; Search does not include join.
 * </pre>
 *
 * <h3>Usage Pattern</h3>
 * <pre>
 *   profiler.start(Component.SEARCH);
 *   // ... search logic ...
 *   profiler.stop(Component.SEARCH);   // pause
 *   profiler.start(Component.JOIN);
 *   // ... join logic ...
 *   profiler.stop(Component.JOIN);
 *   profiler.start(Component.SEARCH);  // resume
 *   // ... more search ...
 *   profiler.stop(Component.SEARCH);
 * </pre>
 * 
 * <p>At the end of mining, call {@link #printReport()} to see the breakdown.
 *
 * @author Meg
 * @version 5.0
 */
public class PerformanceProfiler {

    private final SearchConfig     searchConfig;

    /**
     * The five measurable components of the algorithm.
     * Each corresponds to a distinct strategy in the overall design.
     */
    public enum Component {
        /** Phase 1: PTWU calculation + utility list construction. */
        PREPROCESSING,
        /** Phase 2: Recursive search traversal (DFS/BFS overhead). */
        SEARCH,
        /** Phase 2: Utility list join operation. */
        JOIN,
        /** Phase 2: Pruning condition evaluation. */
        PRUNING,
        /** Phase 2 + 3: Top-K insertion and threshold updates. */
        TOPK
    }

    /** Accumulated exclusive wall-clock time per component (nanoseconds). */
    private final Map<Component, Long> exclusiveTimes;

    /**
     * Per-thread start timestamps.
     * Each thread independently tracks when it last started a component.
     */
    private final Map<Component, Long> startTimes;

    /**
     * Constructs a profiler with all counters zeroed.
     */
    public PerformanceProfiler(SearchConfig searchConfig) {
        this.searchConfig = searchConfig;
        exclusiveTimes = new EnumMap<>(Component.class);
        startTimes     = new EnumMap<>(Component.class);
        for (Component c : Component.values()) {
            exclusiveTimes.put(c, 0L);
            startTimes.put(c, -1L); // -1 indicates not currently running
        }
    }

    /**
     * Starts (or resumes) timing for a component.
     *
     * <p>Records the current wall-clock time in a {@link ThreadLocal}.
     * A subsequent call to {@link #stop(Component)} will accumulate the
     * elapsed duration into the exclusive total.
     *
     * @param component component to start timing
     */
    public void start(Component component) {
        startTimes.put(component, System.nanoTime());
    }

    /**
     * Stops (pauses) timing for a component and accumulates elapsed time.
     *
     * <p>If {@link #start(Component)} was never called on this thread,
     * this method is a no-op.
     *
     * @param component component to stop timing
     */
    public void stop(Component component) {
        long started = startTimes.get(component);
        if (started < 0) return; // start() was never called on this thread

        long elapsed = System.nanoTime() - started;
        exclusiveTimes.put(component, exclusiveTimes.get(component) + elapsed);
        startTimes.put(component, -1L); // mark as stopped
    }

    /**
     * Returns the total exclusive time for a component in milliseconds.
     *
     * @param component component to query
     * @return exclusive time in milliseconds
     */
    public double getExclusiveTimeMs(Component component) {
        return exclusiveTimes.get(component) / 1_000_000.0;
    }

    /**
     * Returns the total wall-clock time across all components in milliseconds.
     *
     * @return sum of exclusive times (ms)
     */
    public double getTotalTimeMs() {
        return exclusiveTimes.values().stream()
                .mapToLong(Long::longValue)
                .sum() / 1_000_000.0;
    }

    /**
     * Returns the percentage of total time spent in a given component.
     *
     * @param component component to query
     * @return percentage [0, 100]
     */
    public double getPercentage(Component component) {
        double total = getTotalTimeMs();
        if (total < 1e-9) return 0.0;
        return (getExclusiveTimeMs(component) / total) * 100.0;
    }

    /**
     * Resets all timers to zero.
     * Should be called between experiment repetitions.
     */
    public void reset() {
        for (Component c : Component.values()) {
            exclusiveTimes.put(c, 0L);
            startTimes.put(c, -1L);
        }
    }

    /**
     * Prints a formatted performance report with a pie-chart-style
     * breakdown of where CPU time is spent.
     *
     * <p>Example output:
     * <pre>
     * === Exclusive Time Profile ===
     * Total profiled time: 1234.56 ms
     * Component          | Time (ms)   | %
     * -------------------|-------------|-------
     * PREPROCESSING      |      123.00 |  9.97%
     * SEARCH             |       45.00 |  3.65%
     * JOIN               |      900.00 | 72.93%
     * PRUNING            |      150.00 | 12.16%
     * TOPK               |       16.56 |  1.34%
     * </pre>
     */
    public void printReport() {
        double total = getTotalTimeMs();
        System.out.println("\n=== Exclusive Time Profile ===");
        System.out.printf("Total profiled time: %.2f ms%n", total);
        System.out.printf("%-20s | %-12s | %s%n", "Component", "Time (ms)", "%");
        System.out.println("-".repeat(50));
        for (Component c : Component.values()) {
            System.out.printf("%-20s | %12.2f | %6.2f%%%n",
                    c.name(), getExclusiveTimeMs(c), getPercentage(c));
        }

        System.out.println("\nSEARCH: " + searchConfig.getStrategyType());
    }

    /**
     * Returns a structured map of component → exclusive time (ms)
     * for use by {@link ReportGenerator}.
     *
     * @return map of component → time in milliseconds
     */
    public Map<Component, Double> getTimingsMs() {
        Map<Component, Double> result = new EnumMap<>(Component.class);
        for (Component c : Component.values()) {
            result.put(c, getExclusiveTimeMs(c));
        }
        return result;
    }
}