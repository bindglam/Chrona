package org.wrd.chrona.async.commit;

import org.jetbrains.annotations.Nullable;

/**
 * Represents an async computation result that needs to be applied on the main thread.
 *
 * <p>Key design principles:
 * <ul>
 *   <li>Commits are validated before application (stale results are dropped)</li>
 *   <li>Commits can be coalesced (newer replaces older for same key)</li>
 *   <li>Commits track their creation tick for staleness detection</li>
 * </ul>
 *
 * <p>Implementation note: Implementations should be lightweight and avoid
 * holding references to NMS objects. Store only primitive data or immutable snapshots.
 */
public interface Commit {

    /**
     * The tick number when this commit was created.
     * Used for staleness validation.
     */
    long getTickStamp();

    /**
     * The phase during which this commit should be processed.
     */
    CommitPhase getPhase();

    /**
     * Validates whether this commit is still applicable.
     * Called on main thread immediately before apply().
     *
     * <p>Should check:
     * <ul>
     *   <li>Target entity is still alive</li>
     *   <li>Target is in the same world</li>
     *   <li>Relevant chunks are still loaded</li>
     *   <li>Commit hasn't become stale (too many ticks passed)</li>
     * </ul>
     *
     * @return true if commit should be applied, false to silently drop
     */
    boolean validate();

    /**
     * Applies this commit's changes to the world.
     * Called on main thread, only if validate() returned true.
     *
     * <p>This is the ONLY place where world state modifications should occur.
     * All Bukkit events should be fired from within this method.
     */
    void apply();

    /**
     * Returns a coalescing key for this commit, or null if not coalescable.
     *
     * <p>When multiple commits have the same non-null coalescing key,
     * only the most recent one is kept. This is useful for:
     * <ul>
     *   <li>Pathfinding: Only the latest path matters per entity</li>
     *   <li>Tracking: Only the latest visibility state matters per player-entity pair</li>
     * </ul>
     *
     * @return coalescing key, or null if this commit cannot be coalesced
     */
    @Nullable
    default Object getCoalesceKey() {
        return null;
    }

    /**
     * Called when this commit is being replaced by a newer one with the same coalesce key.
     * Use this to clean up any resources or return objects to pools.
     */
    default void onCoalesced() {
        // Default: no cleanup needed
    }

    /**
     * Called when this commit is dropped due to validation failure or queue overflow.
     * Use this to clean up any resources or return objects to pools.
     */
    default void onDropped() {
        // Default: no cleanup needed
    }

    /**
     * Priority hint for processing order within the same phase.
     * Higher values = processed earlier. Default is 0.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Maximum number of ticks this commit remains valid.
     * After this many ticks, validate() should return false.
     * Default is 20 ticks (1 second).
     */
    default int getMaxStaleTicks() {
        return 20;
    }
}
