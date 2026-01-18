package org.wrd.chrona.blocks;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages sleeping block entities to optimize ticking
 * Based on Lithium mod's sleeping block entity system
 */
public class SleepingBlockEntityManager {
    private static final Logger LOGGER = LogManager.getLogger(SleepingBlockEntityManager.class);

    // Map of block position to wake time
    private final Long2LongOpenHashMap sleepingEntities = new Long2LongOpenHashMap();
    private final Level level;

    public SleepingBlockEntityManager(Level level) {
        this.level = level;
    }

    /**
     * Register a block entity to sleep until a specific time
     */
    public void sleepUntil(BlockPos pos, long wakeTime) {
        sleepingEntities.put(pos.asLong(), wakeTime);
    }

    /**
     * Wake up a block entity immediately
     */
    public void wakeUp(BlockPos pos) {
        sleepingEntities.remove(pos.asLong());
    }

    /**
     * Check if a block entity is sleeping
     */
    public boolean isSleeping(BlockPos pos) {
        return sleepingEntities.containsKey(pos.asLong());
    }

    /**
     * Tick the sleeping system - wake up entities whose time has come
     * @param currentTime current game time
     * @return list of positions that should be woken up
     */
    public List<BlockPos> tickSleepingEntities(long currentTime) {
        List<BlockPos> toWake = new ArrayList<>();

        // Find all entities that should wake up
        sleepingEntities.long2LongEntrySet().removeIf(entry -> {
            long posLong = entry.getLongKey();
            long wakeTime = entry.getLongValue();

            if (currentTime >= wakeTime) {
                toWake.add(BlockPos.of(posLong));
                return true; // Remove from sleeping set
            }
            return false;
        });

        return toWake;
    }

    /**
     * Get the number of sleeping block entities
     */
    public int getSleepingCount() {
        return sleepingEntities.size();
    }

    /**
     * Clear all sleeping entities (for world unload, etc.)
     */
    public void clear() {
        sleepingEntities.clear();
    }

    /**
     * Check if a block entity should be ticked
     * @param blockEntity the block entity to check
     * @return true if should tick, false if sleeping
     */
    public boolean shouldTick(BlockEntity blockEntity) {
        if (!(blockEntity instanceof SleepingBlockEntity sleeper)) {
            return true; // Not a sleeping block entity, always tick
        }

        BlockPos pos = blockEntity.getBlockPos();
        if (!sleepingEntities.containsKey(pos.asLong())) {
            return true; // Not currently sleeping
        }

        // Check if should wake up
        long currentTime = level.getGameTime();
        long wakeTime = sleepingEntities.get(pos.asLong());

        if (currentTime >= wakeTime) {
            // Time to wake up
            sleepingEntities.remove(pos.asLong());
            sleeper.lithium$wakeUpNow();
            return true;
        }

        return false; // Still sleeping
    }
}
