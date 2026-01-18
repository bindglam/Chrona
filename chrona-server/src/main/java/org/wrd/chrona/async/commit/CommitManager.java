package org.wrd.chrona.async.commit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.wrd.chrona.configuration.ChronaConfiguration;

/**
 * Central manager for the commit queue system.
 * Integrates with server tick cycle to process commits at appropriate phases.
 *
 * <p>Usage from server tick:
 * <pre>{@code
 * // At tick start
 * CommitManager.INSTANCE.onTickStart(tickCount);
 *
 * // Before entity tick
 * CommitManager.INSTANCE.processPhase(CommitPhase.PRE_ENTITY_TICK);
 *
 * // After entity tick
 * CommitManager.INSTANCE.processPhase(CommitPhase.POST_ENTITY_TICK);
 *
 * // Before network flush
 * CommitManager.INSTANCE.processPhase(CommitPhase.PRE_NETWORK_FLUSH);
 * }</pre>
 *
 * <p>Usage from async workers:
 * <pre>{@code
 * CommitManager.INSTANCE.enqueue(myCommit);
 * }</pre>
 */
public class CommitManager {

    private static final Logger LOGGER = LogManager.getLogger("Chrona CommitManager");

    // Singleton instance
    public static final CommitManager INSTANCE = new CommitManager();

    private PhasedCommitQueue commitQueue;
    private volatile boolean initialized = false;

    // Configuration
    private int maxQueueSize = 4096;
    private int maxCoalesceMapSize = 2048;
    private boolean logStats = false;
    private int statsLogInterval = 1200; // Every minute at 20 TPS

    private long lastStatsLog = 0;

    private CommitManager() {
        // Private constructor for singleton
    }

    /**
     * Initialize the commit manager with configuration.
     * Must be called once during server startup.
     */
    public void init(ChronaConfiguration config) {
        if (initialized) {
            LOGGER.warn("CommitManager already initialized");
            return;
        }

        // Load configuration if available
        if (config != null) {
            this.maxQueueSize = config.optimization.async.commitQueue.maxQueueSizePerPhase.getValue();
            this.maxCoalesceMapSize = config.optimization.async.commitQueue.coalesceMapSize.getValue();
            this.logStats = config.optimization.async.commitQueue.logStats.getValue();
            this.statsLogInterval = config.optimization.async.commitQueue.statsIntervalTicks.getValue();
        }

        this.commitQueue = new PhasedCommitQueue(maxQueueSize, maxCoalesceMapSize);
        this.initialized = true;

        LOGGER.info("CommitManager initialized with queue size {}, coalesce map size {}, log stats: {}",
                maxQueueSize, maxCoalesceMapSize, logStats);
    }

    /**
     * Initialize with default settings.
     */
    public void init() {
        init(null);
    }

    /**
     * Ensure manager is initialized.
     */
    private void ensureInitialized() {
        if (!initialized) {
            // Auto-initialize with defaults if not done
            init();
        }
    }

    /**
     * Called at the very start of each server tick.
     * Updates the current tick and processes TICK_START phase.
     *
     * @param tickCount current server tick number
     */
    public void onTickStart(long tickCount) {
        ensureInitialized();
        commitQueue.setCurrentTick(tickCount);
        processPhase(CommitPhase.TICK_START);

        // Log stats periodically
        if (logStats && tickCount - lastStatsLog >= statsLogInterval) {
            logStatistics();
            lastStatsLog = tickCount;
        }
    }

    /**
     * Process commits for a specific phase.
     * Must be called from main thread at the appropriate time.
     *
     * @param phase the phase to process
     * @return number of commits processed
     */
    public int processPhase(@NotNull CommitPhase phase) {
        ensureInitialized();
        return commitQueue.processPhase(phase);
    }

    /**
     * Enqueue a commit from any thread.
     *
     * @param commit the commit to enqueue
     * @return true if enqueued, false if dropped due to backpressure
     */
    public boolean enqueue(@NotNull Commit commit) {
        ensureInitialized();
        return commitQueue.enqueue(commit);
    }

    /**
     * Get current tick number.
     */
    public long getCurrentTick() {
        ensureInitialized();
        return commitQueue.getCurrentTick();
    }

    /**
     * Check if system is under backpressure.
     */
    public boolean isUnderPressure() {
        ensureInitialized();
        return commitQueue.isUnderPressure();
    }

    /**
     * Get queue size for a specific phase.
     */
    public int getQueueSize(CommitPhase phase) {
        ensureInitialized();
        return commitQueue.getQueueSize(phase);
    }

    /**
     * Get total pending commits across all phases.
     */
    public int getTotalPending() {
        ensureInitialized();
        return commitQueue.getTotalQueueSize();
    }

    /**
     * Log current statistics.
     */
    public void logStatistics() {
        if (!initialized) return;

        LOGGER.info("CommitQueue Stats - Enqueued: {}, Processed: {}, Dropped: {}, Coalesced: {}, Pending: {}",
                commitQueue.getTotalEnqueued(),
                commitQueue.getTotalProcessed(),
                commitQueue.getTotalDropped(),
                commitQueue.getTotalCoalesced(),
                commitQueue.getTotalQueueSize());
    }

    /**
     * Enable or disable periodic stats logging.
     */
    public void setLogStats(boolean enabled) {
        this.logStats = enabled;
    }

    /**
     * Set stats log interval in ticks.
     */
    public void setStatsLogInterval(int ticks) {
        this.statsLogInterval = ticks;
    }

    /**
     * Get statistics snapshot.
     */
    public CommitStats getStats() {
        ensureInitialized();
        return new CommitStats(
                commitQueue.getTotalEnqueued(),
                commitQueue.getTotalProcessed(),
                commitQueue.getTotalDropped(),
                commitQueue.getTotalCoalesced(),
                commitQueue.getTotalQueueSize()
        );
    }

    /**
     * Reset statistics.
     */
    public void resetStats() {
        if (initialized) {
            commitQueue.resetStats();
        }
    }

    /**
     * Shutdown and clear all queues.
     */
    public void shutdown() {
        if (initialized) {
            LOGGER.info("Shutting down CommitManager...");
            logStatistics();
            commitQueue.clear();
        }
    }

    /**
     * Statistics snapshot record.
     */
    public record CommitStats(
            long totalEnqueued,
            long totalProcessed,
            long totalDropped,
            long totalCoalesced,
            int pending
    ) {
        public double getDropRate() {
            return totalEnqueued == 0 ? 0.0 : (double) totalDropped / totalEnqueued;
        }

        public double getCoalesceRate() {
            return totalEnqueued == 0 ? 0.0 : (double) totalCoalesced / totalEnqueued;
        }
    }
}
