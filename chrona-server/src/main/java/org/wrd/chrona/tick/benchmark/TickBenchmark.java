package org.wrd.chrona.tick.benchmark;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

public class TickBenchmark {
    private final LongList snapshotTimes = new LongArrayList();
    private final LongList computeTimes = new LongArrayList();
    private final LongList mergeTimes = new LongArrayList();
    private final LongList commitTimes = new LongArrayList();
    private final LongList pluginTimes = new LongArrayList();

    private long currentPhaseStart;

    public void startSnapshot() {
        currentPhaseStart = System.nanoTime();
    }

    public void endSnapshot() {
        snapshotTimes.add(System.nanoTime() - currentPhaseStart);
    }

    public void startCompute() {
        currentPhaseStart = System.nanoTime();
    }

    public void endCompute() {
        computeTimes.add(System.nanoTime() - currentPhaseStart);
    }

    public void startMerge() {
        currentPhaseStart = System.nanoTime();
    }

    public void endMerge() {
        mergeTimes.add(System.nanoTime() - currentPhaseStart);
    }

    public void startCommit() {
        currentPhaseStart = System.nanoTime();
    }

    public void endCommit() {
        commitTimes.add(System.nanoTime() - currentPhaseStart);
    }

    public void startPlugin() {
        currentPhaseStart = System.nanoTime();
    }

    public void endPlugin() {
        pluginTimes.add(System.nanoTime() - currentPhaseStart);
    }

    public BenchmarkResult getResult() {
        return new BenchmarkResult(
            average(snapshotTimes),
            average(computeTimes),
            average(mergeTimes),
            average(commitTimes),
            average(pluginTimes)
        );
    }

    private double average(LongList times) {
        if (times.isEmpty()) return 0.0;
        long sum = 0;
        for (long time : times) sum += time;
        return sum / (double) times.size() / 1_000_000.0;
    }

    public record BenchmarkResult(
        double snapshotMs,
        double computeMs,
        double mergeMs,
        double commitMs,
        double pluginMs
    ) {
        public double totalMs() {
            return snapshotMs + computeMs + mergeMs + commitMs + pluginMs;
        }

        @Override
        public String toString() {
            return String.format(
                "Snapshot: %.2fms | Compute: %.2fms | Merge: %.2fms | Commit: %.2fms | Plugin: %.2fms | Total: %.2fms",
                snapshotMs, computeMs, mergeMs, commitMs, pluginMs, totalMs()
            );
        }
    }
}
