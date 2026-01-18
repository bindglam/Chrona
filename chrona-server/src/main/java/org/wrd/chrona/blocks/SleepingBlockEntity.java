package org.wrd.chrona.blocks;

/**
 * Interface for block entities that can sleep to skip ticking
 * Inspired by Lithium mod's block entity optimization
 *
 * Block entities implementing this interface can enter a "sleeping" state
 * where they are not ticked, reducing CPU overhead for inactive blocks.
 */
public interface SleepingBlockEntity {

    /**
     * Start sleeping - block entity will not be ticked
     */
    void lithium$startSleeping();

    /**
     * Wake up immediately - resume ticking on next tick
     */
    void lithium$wakeUpNow();

    /**
     * Check if currently sleeping
     */
    boolean lithium$isSleeping();

    /**
     * Sleep until a specific game time
     * @param wakeTime the game time to wake up at
     */
    void lithium$sleepUntil(long wakeTime);

    /**
     * Sleep for a specific number of ticks
     * @param ticks number of ticks to sleep
     */
    default void lithium$sleepFor(int ticks) {
        // Implementation requires access to world time
        // Will be implemented in actual block entity classes
    }

    /**
     * Sleep only for the current tick
     * Useful for blocks that check conditions frequently
     */
    default void lithium$sleepOnlyCurrentTick() {
        lithium$sleepFor(1);
    }

    /**
     * Check if block entity should wake up
     * @param currentTime current game time
     * @return true if should wake up
     */
    boolean lithium$shouldWakeUp(long currentTime);
}
