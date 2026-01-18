package org.wrd.chrona.tick.phase;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.common.list.ReferenceList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.wrd.chrona.util.FastCollections;

import java.util.List;
import java.util.function.Predicate;

public class WorldSnapshot {
    private static final Predicate<BlockState> IS_REDSTONE_WIRE =
        state -> state.getBlock() instanceof RedStoneWireBlock;

    private final ServerLevel level;
    private final long tickNumber;
    private final Long2ObjectMap<BlockState> blockStates;
    private final Int2ObjectMap<EntitySnapshot> entities;
    private final Entity[] entityArray;
    private LongList redstonePositions;
    private boolean redstoneTruncated;

    private WorldSnapshot(ServerLevel level, long tickNumber) {
        this.level = level;
        this.tickNumber = tickNumber;
        this.blockStates = FastCollections.newLong2ObjectMap(1024);
        this.entities = FastCollections.newInt2ObjectMap(256);
        this.entityArray = captureEntities(level);
    }

    public static WorldSnapshot capture(ServerLevel level, long tickNumber) {
        return new WorldSnapshot(level, tickNumber);
    }

    private Entity[] captureEntities(ServerLevel level) {
        Iterable<Entity> allEntities = level.getAllEntities();
        int count = 0;
        for (Entity ignored : allEntities) count++;

        Entity[] array = new Entity[count];
        int i = 0;
        for (Entity entity : allEntities) {
            array[i++] = entity;

            float health = 0f;
            boolean onGround = entity.onGround();
            boolean inWater = entity.isInWater();
            Integer targetId = null;
            float yaw = entity.getYRot();
            float pitch = entity.getXRot();
            boolean isMob = entity instanceof Mob;
            boolean isLiving = entity instanceof LivingEntity;
            boolean isPlayer = entity instanceof Player;

            if (entity instanceof LivingEntity living) {
                health = living.getHealth();
            }

            if (entity instanceof Mob mob) {
                LivingEntity target = mob.getTarget();
                if (target != null) {
                    targetId = target.getId();
                }
            }

            entities.put(
                entity.getId(),
                new EntitySnapshot(
                    entity.getId(),
                    entity.position(),
                    entity.getDeltaMovement(),
                    health,
                    onGround,
                    inWater,
                    targetId,
                    yaw,
                    pitch,
                    isMob,
                    isLiving,
                    isPlayer
                )
            );
        }

        return array;
    }

    public BlockState getBlockState(BlockPos pos) {
        long key = pos.asLong();
        BlockState cached = blockStates.get(key);
        if (cached != null) {
            return cached;
        }

        LevelChunk chunk = level.getChunkAt(pos);
        BlockState state = chunk.getBlockState(pos);
        blockStates.put(key, state);
        return state;
    }

    public EntitySnapshot getEntity(int entityId) {
        return entities.get(entityId);
    }

    public Entity[] getEntityArray() {
        return entityArray;
    }

    public LongList getRedstonePositions(int maxPositions) {
        if (redstonePositions != null) {
            return redstonePositions;
        }

        LongArrayList positions = new LongArrayList(Math.min(Math.max(maxPositions, 0), 1024));
        redstonePositions = positions;
        redstoneTruncated = false;
        if (maxPositions <= 0) {
            return positions;
        }

        if (!(level instanceof ChunkSystemServerLevel chunkLevel)) {
            return positions;
        }

        ReferenceList<LevelChunk> chunks = chunkLevel.moonrise$getEntityTickingChunks();
        LevelChunk[] raw = chunks.getRawDataUnchecked();
        int size = chunks.size();

        boolean limitReached = false;
        scan:
        for (int i = 0; i < size; i++) {
            LevelChunk chunk = raw[i];
            if (chunk == null) continue;

            LevelChunkSection[] sections = chunk.getSections();
            int baseX = chunk.locX << 4;
            int baseZ = chunk.locZ << 4;

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                if (!section.maybeHas(IS_REDSTONE_WIRE)) {
                    continue;
                }

                int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.getBlock() instanceof RedStoneWireBlock) {
                                positions.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                                if (positions.size() >= maxPositions) {
                                    limitReached = true;
                                    break scan;
                                }
                            }
                        }
                    }
                }
            }
        }

        redstoneTruncated = limitReached;

        return positions;
    }

    public boolean isRedstoneTruncated() {
        return redstoneTruncated;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public long getTickNumber() {
        return tickNumber;
    }

    /**
     * Get entities within a radius of a position (for AI target searching)
     * @param center Center position
     * @param radius Search radius
     * @return List of entity snapshots within radius
     */
    public List<EntitySnapshot> getEntitiesInRadius(Vec3 center, double radius) {
        ObjectArrayList<EntitySnapshot> result = FastCollections.newObjectList();
        double radiusSq = radius * radius;

        for (EntitySnapshot snapshot : entities.values()) {
            double distSq = snapshot.position().distanceToSqr(center);
            if (distSq <= radiusSq) {
                result.add(snapshot);
            }
        }

        return result;
    }

    /**
     * Get entities within an AABB
     * @param box Bounding box to search
     * @return List of entity snapshots within the AABB
     */
    public List<EntitySnapshot> getEntitiesInAABB(AABB box) {
        ObjectArrayList<EntitySnapshot> result = FastCollections.newObjectList();

        for (EntitySnapshot snapshot : entities.values()) {
            Vec3 pos = snapshot.position();
            if (box.contains(pos)) {
                result.add(snapshot);
            }
        }

        return result;
    }

    /**
     * Check if a position has collision (solid block)
     * @param pos Block position to check
     * @return true if the block has collision
     */
    public boolean hasCollision(BlockPos pos) {
        BlockState state = getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Check if a position is in water
     * @param pos Block position to check
     * @return true if the block is water
     */
    public boolean isWater(BlockPos pos) {
        BlockState state = getBlockState(pos);
        return state.getFluidState().isSource();
    }

    /**
     * Immutable snapshot of entity state at tick capture time
     */
    public record EntitySnapshot(
        int id,
        Vec3 position,
        Vec3 velocity,
        float health,
        boolean onGround,
        boolean inWater,
        @Nullable Integer targetId,
        float yaw,
        float pitch,
        boolean isMob,
        boolean isLiving,
        boolean isPlayer
    ) {
        /**
         * Legacy constructor for compatibility
         */
        public EntitySnapshot(int id, Vec3 position, Vec3 velocity, float health) {
            this(id, position, velocity, health, false, false, null, 0f, 0f, false, false, false);
        }
    }
}
