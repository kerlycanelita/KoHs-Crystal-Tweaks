package com.zymekoh.crystaltweaks.client.benchmark;

import java.util.Arrays;

/**
 * Summary of one measured quantity, in milliseconds. Built once when a run ends; nothing here is
 * estimated, every field comes from the samples themselves.
 */
public record BenchmarkStats(int count, double mean, double median, double p95, double p99, double min,
        double max, double stdDev) {
    public static final BenchmarkStats EMPTY = new BenchmarkStats(0, 0, 0, 0, 0, 0, 0, 0);

    public boolean present() {
        return this.count > 0;
    }

    /** Stats of {@code values[0..length)}, which it sorts. */
    public static BenchmarkStats of(double[] values, int length) {
        if (values == null || length <= 0) {
            return EMPTY;
        }
        double[] sorted = Arrays.copyOf(values, length);
        Arrays.sort(sorted);
        double sum = 0;
        for (double value : sorted) {
            sum += value;
        }
        double mean = sum / length;
        double squares = 0;
        for (double value : sorted) {
            squares += (value - mean) * (value - mean);
        }
        return new BenchmarkStats(length, mean, percentile(sorted, 0.50), percentile(sorted, 0.95),
                percentile(sorted, 0.99), sorted[0], sorted[length - 1], Math.sqrt(squares / length));
    }

    /** Nearest-rank percentile of already sorted values. */
    static double percentile(double[] sorted, double fraction) {
        if (sorted.length == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank))];
    }
}
