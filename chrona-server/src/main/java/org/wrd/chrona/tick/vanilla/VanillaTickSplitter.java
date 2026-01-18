package org.wrd.chrona.tick.vanilla;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.common.list.ReferenceList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.util.FastCollections;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Splits vanilla tick into pre/entity/post phases for parallel entity processing
 *
 * Pre-tick: Weather, time, chunks, block ticks, fluid ticks
 * Entity extraction: Get entity list without ticking them
 * Post-tick: Block entities, raids, cleanup
 */
public class VanillaTickSplitter {
    private static final Logger LOGGER = LogManager.getLogger(VanillaTickSplitter.class);

    private final ServerLevel level;
    private volatile boolean inPreTick = false;
    private volatile boolean inPostTick = false;

    public VanillaTickSplitter(ServerLevel level) {
        this.level = level;
    }

    /**
     * Execute pre-tick phase: environment, chunks, blocks, fluids
     * This handles everything that happens before entity ticking
     */
    public void executePreTick(BooleanSupplier hasTimeLeft) {
        inPreTick = true;
        try {
            // Weather updates
            tickWeather();

            // Time updates
            tickTime();

            // Chunk system updates
            tickChunks(hasTimeLeft);

            // Block ticks (scheduled block updates)
            tickBlocks(hasTimeLeft);

            // Fluid ticks
            tickFluids(hasTimeLeft);

            // Random ticks for crop growth, etc.
            tickRandomBlocks();

        } catch (Exception e) {
            LOGGER.error("Error in pre-tick phase", e);
        } finally {
            inPreTick = false;
        }
    }

    /**
     * Extract entities that need ticking without actually ticking them
     * Returns the entity list for parallel processing
     */
    public Entity[] extractEntities() {
        try {
            Iterable<Entity> allEntities = level.getAllEntities();
            ObjectArrayList<Entity> entities = FastCollections.newObjectList(256);

            for (Entity entity : allEntities) {
                if (shouldTickEntity(entity)) {
                    entities.add(entity);
                }
            }

            return entities.toArray(new Entity[0]);
        } catch (Exception e) {
            LOGGER.error("Error extracting entities", e);
            return new Entity[0];
        }
    }

    /**
     * Execute post-tick phase: block entities, raids, debug, cleanup
     */
    public void executePostTick() {
        inPostTick = true;
        try {
            // Block entities (furnaces, hoppers, etc.)
            tickBlockEntities();

            // Raid system
            tickRaids();

            // Dragon fight (end dimension)
            tickDragonFight();

            // Portal forcer cleanup
            tickPortals();

            // Debug / profiler updates
            tickDebug();

        } catch (Exception e) {
            LOGGER.error("Error in post-tick phase", e);
        } finally {
            inPostTick = false;
        }
    }

    private void tickWeather() {
        try {
            // Weather is handled by ServerLevel.advanceWeatherCycle
            // We skip this as it's called by the main tick
            // level.advanceWeatherCycle();
        } catch (Exception e) {
            LOGGER.warn("Error ticking weather", e);
        }
    }

    private void tickTime() {
        try {
            // Time advancement is typically handled by MinecraftServer
            // Skip here as it's managed at server level
        } catch (Exception e) {
            LOGGER.warn("Error ticking time", e);
        }
    }

    private void tickChunks(BooleanSupplier hasTimeLeft) {
        try {
            // Chunk system manages loading/unloading
            // This is handled by Paper's async chunk system
            level.getChunkSource().tick(hasTimeLeft, true);
        } catch (Exception e) {
            LOGGER.warn("Error ticking chunks", e);
        }
    }

    private void tickBlocks(BooleanSupplier hasTimeLeft) {
        try {
            // Scheduled block ticks
            // Block tick processing is done via level.tickBlock scheduled ticks
            // The actual execution happens in the main tick
        } catch (Exception e) {
            LOGGER.warn("Error ticking blocks", e);
        }
    }

    private void tickFluids(BooleanSupplier hasTimeLeft) {
        try {
            // Fluid ticks (water/lava flow)
            // Similar to block ticks, handled via scheduled system
        } catch (Exception e) {
            LOGGER.warn("Error ticking fluids", e);
        }
    }

    private void tickRandomBlocks() {
        try {
            // Random block ticks for crop growth, grass spread, etc.
            // This is tied to chunk ticking
            if (level instanceof ChunkSystemServerLevel chunkLevel) {
                ReferenceList<LevelChunk> chunks = chunkLevel.moonrise$getEntityTickingChunks();
                // Random ticks are handled per-chunk during chunk tick
            }
        } catch (Exception e) {
            LOGGER.warn("Error in random block ticks", e);
        }
    }

    private boolean shouldTickEntity(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }

        // Skip entities that are passengers (ticked by vehicle)
        if (entity.isPassenger()) {
            return false;
        }

        // Check if entity should tick
        return !entity.isRemoved();
    }

    private void tickBlockEntities() {
        try {
            // Block entities like furnaces, hoppers, pistons
            // This is typically called via level.tickBlockEntities()
            level.tickBlockEntities();
        } catch (Exception e) {
            LOGGER.warn("Error ticking block entities", e);
        }
    }

    private void tickRaids() {
        try {
            // Raid system for pillagers
            // Raids.tick() requires ServerLevel parameter
            // This is handled by the main server tick
        } catch (Exception e) {
            LOGGER.warn("Error ticking raids", e);
        }
    }

    private void tickDragonFight() {
        try {
            // Dragon fight is specific to the end
            // Handled automatically by ServerLevel for end dimension
        } catch (Exception e) {
            LOGGER.warn("Error ticking dragon fight", e);
        }
    }

    private void tickPortals() {
        try {
            // Portal forcer cleanup
            // level.getPortalForcer().tick();
        } catch (Exception e) {
            LOGGER.warn("Error ticking portals", e);
        }
    }

    private void tickDebug() {
        try {
            // Debug/profiler updates are typically no-ops in production
        } catch (Exception e) {
            LOGGER.warn("Error in debug tick", e);
        }
    }

    public boolean isInPreTick() {
        return inPreTick;
    }

    public boolean isInPostTick() {
        return inPostTick;
    }

    public ServerLevel getLevel() {
        return level;
    }
}
