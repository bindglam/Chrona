package org.wrd.chrona.async.commit;

/**
 * Defines the phases during a server tick when commits can be processed.
 * Each phase has a specific purpose and timing guarantee for plugin compatibility.
 *
 * <p>Processing order within a tick:
 * <ol>
 *   <li>TICK_START - Previous tick's deferred results</li>
 *   <li>PRE_ENTITY_TICK - Before entity AI/movement processing</li>
 *   <li>POST_ENTITY_TICK - After entity processing, before block ticks</li>
 *   <li>PRE_NETWORK_FLUSH - Before packets are flushed to players</li>
 * </ol>
 */
public enum CommitPhase {

    /**
     * Processed at the very start of a tick.
     * Safe for: General state changes from previous tick's async work.
     * Use case: Pathfinding results, pre-calculated spawn positions.
     */
    TICK_START(0, "tick_start"),

    /**
     * Processed before entity tick loop begins.
     * Safe for: Changes that entities should observe during their tick.
     * Use case: Navigation updates, AI goal modifications.
     */
    PRE_ENTITY_TICK(1, "pre_entity_tick"),

    /**
     * Processed after all entities have ticked.
     * Safe for: Changes based on entity tick results.
     * Use case: Tracking updates, collision results.
     */
    POST_ENTITY_TICK(2, "post_entity_tick"),

    /**
     * Processed just before network flush.
     * Safe for: Packet preparation, visibility changes.
     * Use case: Chunk packet sending, entity tracking packets.
     */
    PRE_NETWORK_FLUSH(3, "pre_network_flush");

    private final int order;
    private final String configKey;

    CommitPhase(int order, String configKey) {
        this.order = order;
        this.configKey = configKey;
    }

    public int getOrder() {
        return order;
    }

    public String getConfigKey() {
        return configKey;
    }

    /**
     * Get total number of phases.
     */
    public static int count() {
        return values().length;
    }
}
