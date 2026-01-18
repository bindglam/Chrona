package org.wrd.chrona.tick.phase;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.config.ChronaConfig;

import java.util.List;
import java.util.function.Consumer;

public class CommitPhase {
    private static final Logger LOGGER = LogManager.getLogger(CommitPhase.class);
    private static final boolean DEBUG_LOGGING = false; // Set to true to debug intent application

    private final ServerLevel level;
    private int appliedBlocks = 0;
    private int appliedEntities = 0;
    private int appliedChunks = 0;

    public CommitPhase(ServerLevel level) {
        this.level = level;
    }

    public void apply(List<Intent> intents) {
        appliedBlocks = 0;
        appliedEntities = 0;
        appliedChunks = 0;

        for (Intent intent : intents) {
            try {
                switch (intent) {
                    case BlockIntent bi -> {
                        applyBlockIntent(bi);
                        appliedBlocks++;
                    }
                    case EntityIntent ei -> {
                        applyEntityIntent(ei);
                        appliedEntities++;
                    }
                    case ChunkIntent ci -> {
                        applyChunkIntent(ci);
                        appliedChunks++;
                    }
                    default -> LOGGER.warn("Unknown intent type: {}", intent.getClass());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to apply intent from source {}: {}", intent.getSource(), e.getMessage());
                if (DEBUG_LOGGING) {
                    LOGGER.error("Intent details", e);
                }
            }
        }

        if (DEBUG_LOGGING && (appliedBlocks > 0 || appliedEntities > 0 || appliedChunks > 0)) {
            LOGGER.info("Applied {} block intents, {} entity intents, {} chunk intents",
                appliedBlocks, appliedEntities, appliedChunks);
        }
    }

    private void applyBlockIntent(BlockIntent intent) {
        switch (intent.type()) {
            case BLOCK_SET -> {
                if (intent.newState() != null) {
                    if (ChronaConfig.redstoneValidateOnCommit && "redstone".equals(intent.source())) {
                        BlockState current = level.getBlockState(intent.pos());
                        if (current.getBlock() instanceof RedStoneWireBlock wire) {
                            wire.calculateCurrentChanges(level, intent.pos(), current);
                            return;
                        }
                    }
                    // Use flag 3: Update + Notify neighbors + Send to client
                    // Flag 2 would skip client update, flag 1 would skip neighbor updates
                    boolean success = level.setBlock(intent.pos(), intent.newState(), 3);

                    if (DEBUG_LOGGING && !success) {
                        LOGGER.warn("Failed to set block at {} to {} (source: {})",
                            intent.pos(), intent.newState().getBlock(), intent.source());
                    }
                }
            }
            case BLOCK_BREAK -> {
                boolean success = level.destroyBlock(intent.pos(), true);

                if (DEBUG_LOGGING && !success) {
                    LOGGER.warn("Failed to break block at {} (source: {})",
                        intent.pos(), intent.source());
                }
            }
        }
    }

    private void applyEntityIntent(EntityIntent intent) {
        Entity entity = level.getEntity(intent.entityId());

        if (entity == null && intent.type() != Intent.IntentType.ENTITY_SPAWN) {
            if (DEBUG_LOGGING) {
                LOGGER.warn("Entity {} not found for intent type {}", intent.entityId(), intent.type());
            }
            return;
        }

        switch (intent.type()) {
            case ENTITY_MOVE -> {
                if (entity != null && !entity.isRemoved()) {
                    // Update position and velocity
                    entity.setPos(intent.position());
                    entity.setDeltaMovement(intent.velocity());

                    if (DEBUG_LOGGING) {
                        LOGGER.debug("Moved entity {} to {} with velocity {}",
                            entity.getId(), intent.position(), intent.velocity());
                    }
                }
            }
            case DAMAGE -> {
                if (entity != null && !entity.isRemoved() && intent.data() instanceof Float amount) {
                    entity.hurt(level.damageSources().generic(), amount);

                    if (DEBUG_LOGGING) {
                        LOGGER.debug("Damaged entity {} for {} HP", entity.getId(), amount);
                    }
                }
            }
            case ENTITY_REMOVE -> {
                if (entity != null && !entity.isRemoved()) {
                    entity.remove(Entity.RemovalReason.DISCARDED);

                    if (DEBUG_LOGGING) {
                        LOGGER.debug("Removed entity {}", entity.getId());
                    }
                }
            }
            case ENTITY_SPAWN -> {
                if (intent.data() instanceof Entity spawnEntity) {
                    if (!spawnEntity.isRemoved()) {
                        level.addFreshEntity(spawnEntity);
                    }
                } else if (DEBUG_LOGGING) {
                    LOGGER.warn("ENTITY_SPAWN missing Entity payload (source: {})", intent.source());
                }
            }
            case INVENTORY_CHANGE -> {
                if (intent.data() instanceof Runnable runnable) {
                    runnable.run();
                } else if (intent.data() instanceof Consumer<?> consumer) {
                    @SuppressWarnings("unchecked")
                    Consumer<Entity> entityConsumer = (Consumer<Entity>) consumer;
                    entityConsumer.accept(entity);
                } else if (DEBUG_LOGGING) {
                    LOGGER.warn("INVENTORY_CHANGE missing runnable/consumer payload (source: {})", intent.source());
                }
            }
        }
    }

    private void applyChunkIntent(ChunkIntent intent) {
        switch (intent.type()) {
            case CHUNK_GEN -> applyChunkGenIntent(intent);
            case STRUCTURE_PLACE -> applyStructurePlaceIntent(intent);
            default -> {
                if (DEBUG_LOGGING) {
                    LOGGER.warn("Unknown chunk intent type: {} (source: {})", intent.type(), intent.source());
                }
            }
        }
    }

    private void applyChunkGenIntent(ChunkIntent intent) {
        ChunkIntent.ChunkData data = intent.chunkData();
        if (data == null || data.isEmpty()) {
            if (DEBUG_LOGGING) {
                LOGGER.debug("Skipping empty chunk gen intent for {},{}", intent.chunkX(), intent.chunkZ());
            }
            return;
        }

        try {
            ChunkPos chunkPos = new ChunkPos(intent.chunkX(), intent.chunkZ());
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);

            if (chunk == null) {
                if (DEBUG_LOGGING) {
                    LOGGER.warn("Chunk not found for gen intent: {},{}", intent.chunkX(), intent.chunkZ());
                }
                return;
            }

            // Apply heightmap data
            if (data.heightmap() != null && data.heightmap().length > 0) {
                // Heightmap application would be done here
                // This is simplified - real implementation would update chunk sections
            }

            // Apply section data
            if (data.sections() != null && data.sections().length > 0) {
                // Section data application would be done here
            }

            if (DEBUG_LOGGING) {
                LOGGER.debug("Applied chunk gen intent for {},{} ({}ns)",
                    intent.chunkX(), intent.chunkZ(), data.generationTimeNanos());
            }

        } catch (Exception e) {
            LOGGER.warn("Error applying chunk gen intent for {},{}: {}",
                intent.chunkX(), intent.chunkZ(), e.getMessage());
        }
    }

    private void applyStructurePlaceIntent(ChunkIntent intent) {
        ChunkIntent.StructureData data = intent.structureData();
        if (data == null || data.isEmpty()) {
            if (DEBUG_LOGGING) {
                LOGGER.debug("Skipping empty structure intent for {},{}", intent.chunkX(), intent.chunkZ());
            }
            return;
        }

        try {
            // Structure placement
            int startX = data.startX();
            int startY = data.startY();
            int startZ = data.startZ();

            if (DEBUG_LOGGING) {
                LOGGER.debug("Placing structure {} at {},{},{} (size: {}x{}x{})",
                    data.structureId(), startX, startY, startZ,
                    data.sizeX(), data.sizeY(), data.sizeZ());
            }

            // In a full implementation, this would:
            // 1. Load the structure template
            // 2. Place blocks according to blockData
            // 3. Spawn any entities
            // 4. Run post-processing

            // For now, just mark the corner with a placeholder
            if (data.requiresGround()) {
                BlockPos cornerPos = new BlockPos(startX, startY, startZ);
                // Verify ground exists
                BlockState groundState = level.getBlockState(cornerPos.below());
                if (groundState.isAir()) {
                    if (DEBUG_LOGGING) {
                        LOGGER.debug("Skipping structure {} - no ground at {},{},{}",
                            data.structureId(), startX, startY, startZ);
                    }
                    return;
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Error applying structure intent {} at {},{},{}: {}",
                data.structureId(), data.startX(), data.startY(), data.startZ(), e.getMessage());
        }
    }
}
