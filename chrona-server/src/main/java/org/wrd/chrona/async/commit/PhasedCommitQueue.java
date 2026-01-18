package org.wrd.chrona.async.commit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.wrd.chrona.util.queue.MpmcQueue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe commit queue with phase separation, coalescing, and backpressure.
 *
 * <p>Design principles:
 * <ul>
 *   <li>One queue per phase for predictable processing order</li>
 *   <li>Coalescing map to keep only latest commit per key</li>
 *   <li>Backpressure via queue capacity limits</li>
 *   <li>Lock-free operations using MpmcQueue</li>
 * </ul>
 */
public class PhasedCommitQueue {

    private static final Logger LOGGER = LogManager.getLogger("Chrona Commit Queue");

    // Configuration
    private final int maxQueueSize;
    private final int maxCoalesceMapSize;

    // Per-phase queues using lock-free MpmcQueue
    @SuppressWarnings("unchecked")
    private final MpmcQueue<Commit>[] phaseQueues = new MpmcQueue[CommitPhase.count()];

    // Coalescing: key -> latest commit (allows newer to replace older)
    private final Map<Object, Commit> coalesceMap = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);
    private final AtomicLong totalCoalesced = new AtomicLong(0);
    private final AtomicInteger[] queueSizes = new AtomicInteger[CommitPhase.count()];

    // Current server tick (updated by main thread)
    private volatile long currentTick = 0;

    public PhasedCommitQueue(int maxQueueSize, int maxCoalesceMapSize) {
        this.maxQueueSize = maxQueueSize;
        this.maxCoalesceMapSize = maxCoalesceMapSize;

        for (int i = 0; i < CommitPhase.count(); i++) {
            phaseQueues[i] = new MpmcQueue<>(Commit.class, maxQueueSize);
            queueSizes[i] = new AtomicInteger(0);
        }
    }

    /**
     * Default constructor with sensible defaults.
     */
    public PhasedCommitQueue() {
        this(4096, 2048);
    }

    /**
     * Update the current tick. Must be called from main thread at tick start.
     */
    public void setCurrentTick(long tick) {
        this.currentTick = tick;
    }

    /**
     * Get the current tick.
     */
    public long getCurrentTick() {
        return currentTick;
    }

    /**
     * Enqueue a commit. Thread-safe, can be called from worker threads.
     *
     * @param commit the commit to enqueue
     * @return true if enqueued, false if dropped due to backpressure
     */
    public boolean enqueue(@NotNull Commit commit) {
        CommitPhase phase = commit.getPhase();
        int phaseIndex = phase.getOrder();
        MpmcQueue<Commit> queue = phaseQueues[phaseIndex];

        // Handle coalescing
        Object coalesceKey = commit.getCoalesceKey();
        if (coalesceKey != null) {
            // Check coalesce map size limit
            if (coalesceMap.size() >= maxCoalesceMapSize && !coalesceMap.containsKey(coalesceKey)) {
                // Map is full and this is a new key - drop
                commit.onDropped();
                totalDropped.incrementAndGet();
                return false;
            }

            // Replace older commit with same key
            Commit older = coalesceMap.put(coalesceKey, commit);
            if (older != null) {
                older.onCoalesced();
                totalCoalesced.incrementAndGet();
                // Don't add to queue - the key is already tracked
                totalEnqueued.incrementAndGet();
                return true;
            }
        }

        // Try to enqueue
        if (!queue.send(commit)) {
            // Queue full - backpressure triggered
            if (coalesceKey != null) {
                coalesceMap.remove(coalesceKey);
            }
            commit.onDropped();
            totalDropped.incrementAndGet();
            LOGGER.warn("Commit queue full for phase {}, dropping commit. Consider increasing queue size.", phase);
            return false;
        }

        queueSizes[phaseIndex].incrementAndGet();
        totalEnqueued.incrementAndGet();
        return true;
    }

    /**
     * Process all commits for a specific phase.
     * Must be called from main thread at the appropriate time.
     *
     * @param phase the phase to process
     * @return number of commits processed
     */
    public int processPhase(@NotNull CommitPhase phase) {
        int phaseIndex = phase.getOrder();
        MpmcQueue<Commit> queue = phaseQueues[phaseIndex];
        int processed = 0;
        int dropped = 0;

        Commit commit;
        while ((commit = queue.recv()) != null) {
            queueSizes[phaseIndex].decrementAndGet();

            // Remove from coalesce map if applicable
            Object coalesceKey = commit.getCoalesceKey();
            if (coalesceKey != null) {
                // Only process if this is still the latest commit for this key
                Commit latest = coalesceMap.get(coalesceKey);
                if (latest != commit) {
                    // This commit was coalesced - skip it
                    continue;
                }
                coalesceMap.remove(coalesceKey);
            }

            // Validate and apply
            try {
                // Check staleness
                long tickAge = currentTick - commit.getTickStamp();
                if (tickAge > commit.getMaxStaleTicks()) {
                    commit.onDropped();
                    dropped++;
                    continue;
                }

                if (commit.validate()) {
                    commit.apply();
                    processed++;
                } else {
                    commit.onDropped();
                    dropped++;
                }
            } catch (Exception e) {
                LOGGER.error("Error processing commit in phase {}", phase, e);
                commit.onDropped();
                dropped++;
            }
        }

        totalProcessed.addAndGet(processed);
        totalDropped.addAndGet(dropped);

        return processed;
    }

    /**
     * Process all phases in order. Convenience method for simple use cases.
     */
    public void processAllPhases() {
        for (CommitPhase phase : CommitPhase.values()) {
            processPhase(phase);
        }
    }

    /**
     * Get the current queue size for a phase.
     */
    public int getQueueSize(CommitPhase phase) {
        return queueSizes[phase.getOrder()].get();
    }

    /**
     * Get total queue size across all phases.
     */
    public int getTotalQueueSize() {
        int total = 0;
        for (AtomicInteger size : queueSizes) {
            total += size.get();
        }
        return total;
    }

    /**
     * Check if queues are experiencing backpressure.
     */
    public boolean isUnderPressure() {
        for (int i = 0; i < CommitPhase.count(); i++) {
            if (queueSizes[i].get() > maxQueueSize * 0.8) {
                return true;
            }
        }
        return false;
    }

    // Statistics getters

    public long getTotalEnqueued() {
        return totalEnqueued.get();
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    public long getTotalDropped() {
        return totalDropped.get();
    }

    public long getTotalCoalesced() {
        return totalCoalesced.get();
    }

    public void resetStats() {
        totalEnqueued.set(0);
        totalProcessed.set(0);
        totalDropped.set(0);
        totalCoalesced.set(0);
    }

    /**
     * Clear all queues. Use with caution - only for shutdown.
     */
    public void clear() {
        for (int i = 0; i < CommitPhase.count(); i++) {
            Commit commit;
            while ((commit = phaseQueues[i].recv()) != null) {
                commit.onDropped();
            }
            queueSizes[i].set(0);
        }
        coalesceMap.clear();
    }
}
