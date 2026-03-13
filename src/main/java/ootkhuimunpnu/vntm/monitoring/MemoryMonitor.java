package ootkhuimunpnu.vntm.monitoring;

/**
 * Tracks JVM heap memory usage during mining.
 *
 * <p>Provides a simple watermark tracking for peak memory and an alert
 * mechanism when usage exceeds a configurable fraction of the JVM limit.
 *
 * @author Meg
 * @version 5.0
 */
public class MemoryMonitor {

    private final Runtime runtime = Runtime.getRuntime();
    private long peakUsageBytes = 0L;

    /** Fraction of maxMemory at which a warning is triggered. */
    private final double alertFraction;

    /**
     * Constructs a MemoryMonitor.
     *
     * @param alertFraction fraction [0,1] of JVM max memory to trigger alert
     */
    public MemoryMonitor(double alertFraction) {
        this.alertFraction = alertFraction;
    }

    /**
     * Samples current heap usage and updates the peak watermark.
     *
     * @return current used bytes
     */
    public long sample() {
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (used > peakUsageBytes) peakUsageBytes = used;
        return used;
    }

    /**
     * Returns {@code true} if the most recent sample exceeded the alert threshold.
     *
     * @return whether memory is critically high
     */
    public boolean isAlerted() {
        return peakUsageBytes > runtime.maxMemory() * alertFraction;
    }

    /** @return peak heap usage in bytes */
    public long getPeakUsageBytes() { return peakUsageBytes; }

    /** @return peak heap usage in MB */
    public double getPeakUsageMb() { return peakUsageBytes / (1024.0 * 1024.0); }
}