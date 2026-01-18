package org.wrd.chrona.diagnostics;

import org.wrd.chrona.tick.AsyncTickExecutor;
import org.wrd.chrona.tick.phase.Intent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TickDiagnostics {
    private static final Map<String, TickMetrics> metricsMap = new ConcurrentHashMap<>();

    public static void recordTick(AsyncTickExecutor executor, long snapshotNs, long computeNs, long mergeNs, long commitNs, long pluginNs) {
        String world = "world";
        TickMetrics metrics = metricsMap.computeIfAbsent(world, k -> new TickMetrics());

        metrics.snapshotTime.add(snapshotNs);
        metrics.computeTime.add(computeNs);
        metrics.mergeTime.add(mergeNs);
        metrics.commitTime.add(commitNs);
        metrics.pluginTime.add(pluginNs);
        metrics.totalTicks++;
    }

    public static Map<String, TickMetrics> getMetrics() {
        return new HashMap<>(metricsMap);
    }

    public static class TickMetrics {
        public final RollingAverage snapshotTime = new RollingAverage(100);
        public final RollingAverage computeTime = new RollingAverage(100);
        public final RollingAverage mergeTime = new RollingAverage(100);
        public final RollingAverage commitTime = new RollingAverage(100);
        public final RollingAverage pluginTime = new RollingAverage(100);
        public long totalTicks = 0;

        public double getTotalAverage() {
            return snapshotTime.getAverage() + computeTime.getAverage() +
                   mergeTime.getAverage() + commitTime.getAverage() + pluginTime.getAverage();
        }
    }

    public static class RollingAverage {
        private final long[] values;
        private int index = 0;
        private int count = 0;

        RollingAverage(int size) {
            this.values = new long[size];
        }

        void add(long value) {
            values[index] = value;
            index = (index + 1) % values.length;
            if (count < values.length) count++;
        }

        public double getAverage() {
            if (count == 0) return 0;
            long sum = 0;
            for (int i = 0; i < count; i++) {
                sum += values[i];
            }
            return (double) sum / count / 1_000_000.0;
        }
    }
}
