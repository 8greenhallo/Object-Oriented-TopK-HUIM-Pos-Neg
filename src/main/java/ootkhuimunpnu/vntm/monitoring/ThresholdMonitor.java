package ootkhuimunpnu.vntm.monitoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks the evolution of the mining threshold over time.
 *
 * <p>Records (timestamp, threshold) pairs so researchers can analyse
 * how quickly the threshold rises — a proxy for pruning effectiveness.
 *
 * @author Meg
 * @version 5.0
 */
public class ThresholdMonitor {

    /** Snapshot of threshold at a point in time. */
    public static class ThresholdSnapshot {
        public final long timestampMs;
        public final double threshold;

        ThresholdSnapshot(long ts, double t) { timestampMs = ts; threshold = t; }
    }

    private final List<ThresholdSnapshot> history = new ArrayList<>();
    private final long startTimeMs;

    /** Constructs a ThresholdMonitor and marks the start time. */
    public ThresholdMonitor() {
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Records the current threshold value with an elapsed timestamp.
     *
     * @param currentThreshold current threshold value
     */
    public void record(double currentThreshold) {
        history.add(new ThresholdSnapshot(
                System.currentTimeMillis() - startTimeMs,
                currentThreshold));
    }

    /**
     * Returns an unmodifiable view of the threshold history.
     *
     * @return list of (time, threshold) snapshots
     */
    public List<ThresholdSnapshot> getHistory() {
        return Collections.unmodifiableList(history);
    }
}