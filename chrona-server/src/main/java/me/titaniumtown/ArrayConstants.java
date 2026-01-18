package me.titaniumtown;

import net.minecraft.world.entity.Entity;

/**
 * Cached empty arrays to reduce allocations.
 *
 * Enum.values() returns a new array copy every time it's called.
 * By caching these arrays, we avoid unnecessary allocations in hot paths.
 *
 * Based on Gale/JettPack optimization.
 */
public final class ArrayConstants {

    private ArrayConstants() {}

    // Primitive arrays
    public static final byte[] emptyByteArray = new byte[0];
    public static final int[] emptyIntArray = new int[0];
    public static final long[] emptyLongArray = new long[0];

    // Common singleton arrays
    public static final int[] zeroSingletonIntArray = new int[]{0};

    // Object arrays
    public static final Object[] emptyObjectArray = new Object[0];
    public static final String[] emptyStringArray = new String[0];
    public static final Entity[] emptyEntityArray = new Entity[0];
    public static final org.bukkit.entity.Entity[] emptyBukkitEntityArray = new org.bukkit.entity.Entity[0];
}
