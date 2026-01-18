package org.wrd.chrona.physics;

/**
 * Entity collision mode inspired by Canvas MC
 * Allows selective optimization of entity collision checks
 */
public enum EntityCollisionMode {
    /**
     * Default Minecraft collision behavior - all entities collide
     */
    VANILLA,

    /**
     * Only players are pushable, large search radius (8 chunks)
     * Useful for servers with large entity farms
     */
    ONLY_PUSHABLE_PLAYERS_LARGE,

    /**
     * Only players are pushable, small search radius (2 chunks)
     * Optimized for standard entity counts
     */
    ONLY_PUSHABLE_PLAYERS_SMALL,

    /**
     * Disable all entity collisions
     * Maximum performance, may break some mechanics
     */
    NO_COLLISIONS;

    /**
     * Check if only players can be pushed
     */
    public boolean onlyPlayersPushable() {
        return this == ONLY_PUSHABLE_PLAYERS_LARGE || this == ONLY_PUSHABLE_PLAYERS_SMALL;
    }

    /**
     * Check if all entities can be pushed
     */
    public boolean allEntitiesCanBePushed() {
        return this == VANILLA;
    }

    /**
     * Check if collisions are completely disabled
     */
    public boolean noCollisions() {
        return this == NO_COLLISIONS;
    }

    /**
     * Check if using large push range
     */
    public boolean isLargePushRange() {
        return this == ONLY_PUSHABLE_PLAYERS_LARGE;
    }

    /**
     * Get the search radius in chunks for entity collision checks
     */
    public int getSearchRadius() {
        return switch (this) {
            case ONLY_PUSHABLE_PLAYERS_LARGE -> 8;
            case ONLY_PUSHABLE_PLAYERS_SMALL -> 2;
            case VANILLA -> 1;
            case NO_COLLISIONS -> 0;
        };
    }

    /**
     * Convert from ordinal for network serialization
     */
    public static EntityCollisionMode fromOrdinal(int ordinal) {
        EntityCollisionMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : VANILLA;
    }
}
