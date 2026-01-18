package org.wrd.chrona.async.spawn;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.tick.phase.EntityIntent;
import org.wrd.chrona.tick.phase.Intent;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.IntentPriority;
import org.wrd.chrona.tick.phase.WorldSnapshot;
import org.wrd.chrona.util.FastCollections;
import org.wrd.chrona.util.lockfree.LockFreeExecutor;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async mob spawning system
 * Parallelizes spawn position calculation and condition evaluation
 * Generates ENTITY_SPAWN intents for the commit phase
 */
public class AsyncMobSpawner {
    private static final Logger LOGGER = LogManager.getLogger(AsyncMobSpawner.class);
    private static final int MAX_SPAWN_ATTEMPTS = 3;

    private final ServerLevel level;
    private final LockFreeExecutor executor;

    // Statistics
    private final AtomicLong totalSpawnAttempts = new AtomicLong(0);
    private final AtomicLong successfulSpawns = new AtomicLong(0);
    private final AtomicLong failedSpawns = new AtomicLong(0);
    private final AtomicLong totalSpawnTime = new AtomicLong(0);

    public AsyncMobSpawner(ServerLevel level, LockFreeExecutor executor) {
        this.level = level;
        this.executor = executor;
        LOGGER.info("AsyncMobSpawner initialized for {}", level.dimension());
    }

    /**
     * Process spawning for a set of chunks in parallel
     * @param chunks Chunks to process for spawning
     * @param snapshot World snapshot for condition evaluation
     * @param intentMerger Intent merger for ENTITY_SPAWN intents
     * @param tickId Current tick ID
     * @return Future that completes when spawning is done
     */
    public CompletableFuture<SpawnResult> processSpawning(
            LevelChunk[] chunks,
            WorldSnapshot snapshot,
            IntentMerger intentMerger,
            long tickId) {

        if (!ChronaConfig.asyncMobSpawning || chunks == null || chunks.length == 0) {
            return CompletableFuture.completedFuture(new SpawnResult(0, 0, 0));
        }

        long startTime = System.nanoTime();

        // Split chunks into batches
        int batchSize = ChronaConfig.mobSpawnBatchSize;
        List<CompletableFuture<BatchResult>> futures = FastCollections.newObjectList();

        for (int i = 0; i < chunks.length; i += batchSize) {
            int end = Math.min(i + batchSize, chunks.length);
            LevelChunk[] batch = new LevelChunk[end - i];
            System.arraycopy(chunks, i, batch, 0, end - i);

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(
                () -> processBatch(batch, snapshot, intentMerger, tickId),
                executor
            );
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                int attempts = 0;
                int spawned = 0;
                int failed = 0;

                for (CompletableFuture<BatchResult> future : futures) {
                    BatchResult result = future.getNow(new BatchResult(0, 0, 0));
                    attempts += result.attempts;
                    spawned += result.spawned;
                    failed += result.failed;
                }

                long elapsed = System.nanoTime() - startTime;
                totalSpawnTime.addAndGet(elapsed);
                totalSpawnAttempts.addAndGet(attempts);
                successfulSpawns.addAndGet(spawned);
                failedSpawns.addAndGet(failed);

                return new SpawnResult(attempts, spawned, elapsed);
            });
    }

    private BatchResult processBatch(
            LevelChunk[] chunks,
            WorldSnapshot snapshot,
            IntentMerger intentMerger,
            long tickId) {

        int attempts = 0;
        int spawned = 0;
        int failed = 0;
        Random random = ThreadLocalRandom.current();

        for (LevelChunk chunk : chunks) {
            if (chunk == null) continue;

            // Process each mob category
            for (MobCategory category : MobCategory.values()) {
                if (!shouldSpawnCategory(category)) {
                    continue;
                }

                // Check spawn cap for category
                if (!canSpawnMoreOfCategory(category)) {
                    continue;
                }

                // Try to find spawn positions
                for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
                    attempts++;

                    SpawnCandidate candidate = findSpawnPosition(chunk, category, snapshot, random);
                    if (candidate == null) {
                        failed++;
                        continue;
                    }

                    // Create spawn intent
                    if (createSpawnIntent(candidate, intentMerger, tickId)) {
                        spawned++;
                    } else {
                        failed++;
                    }
                }
            }
        }

        return new BatchResult(attempts, spawned, failed);
    }

    private boolean shouldSpawnCategory(MobCategory category) {
        // Skip ambient and misc categories for now
        return category == MobCategory.MONSTER ||
               category == MobCategory.CREATURE ||
               category == MobCategory.WATER_CREATURE ||
               category == MobCategory.WATER_AMBIENT;
    }

    private boolean canSpawnMoreOfCategory(MobCategory category) {
        // In a full implementation, this would check against spawn caps
        // For now, always allow
        return true;
    }

    private SpawnCandidate findSpawnPosition(
            LevelChunk chunk,
            MobCategory category,
            WorldSnapshot snapshot,
            Random random) {

        int chunkX = chunk.getPos().x * 16;
        int chunkZ = chunk.getPos().z * 16;

        // Random position within chunk
        int x = chunkX + random.nextInt(16);
        int z = chunkZ + random.nextInt(16);

        // Find spawn height
        int y = findSpawnHeight(snapshot, x, z, category);
        if (y < level.getMinY() || y > level.getMaxY()) {
            return null;
        }

        BlockPos pos = new BlockPos(x, y, z);

        // Check spawn conditions
        if (!isValidSpawnPosition(snapshot, pos, category)) {
            return null;
        }

        // Get entity type for this category
        EntityType<?> entityType = getEntityTypeForCategory(category, random);
        if (entityType == null) {
            return null;
        }

        return new SpawnCandidate(pos, entityType, category);
    }

    private int findSpawnHeight(WorldSnapshot snapshot, int x, int z, MobCategory category) {
        // Simple height finding - start from top and go down
        for (int y = level.getMaxY() - 1; y > level.getMinY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();

            if (!snapshot.hasCollision(pos) && snapshot.hasCollision(below)) {
                return y;
            }
        }
        return level.getMinY() - 1;
    }

    private boolean isValidSpawnPosition(WorldSnapshot snapshot, BlockPos pos, MobCategory category) {
        // Check basic spawn conditions
        if (snapshot.hasCollision(pos)) {
            return false;
        }

        // Check if there's ground below
        if (!snapshot.hasCollision(pos.below())) {
            return false;
        }

        // Check light level for monsters
        if (category == MobCategory.MONSTER) {
            // Monsters need low light (simplified check)
            // In a full implementation, would check actual light level
        }

        return true;
    }

    private EntityType<?> getEntityTypeForCategory(MobCategory category, Random random) {
        // Simplified entity type selection
        // In a full implementation, this would use spawn weights from biomes
        return switch (category) {
            case MONSTER -> random.nextFloat() < 0.5f ?
                EntityType.ZOMBIE : EntityType.SKELETON;
            case CREATURE -> random.nextFloat() < 0.5f ?
                EntityType.PIG : EntityType.COW;
            case WATER_CREATURE -> EntityType.SQUID;
            case WATER_AMBIENT -> EntityType.COD;
            default -> null;
        };
    }

    private boolean createSpawnIntent(SpawnCandidate candidate, IntentMerger intentMerger, long tickId) {
        try {
            // Set position
            Vec3 spawnPos = new Vec3(
                candidate.pos.getX() + 0.5,
                candidate.pos.getY(),
                candidate.pos.getZ() + 0.5
            );

            // Create the entity with proper spawn reason
            Entity entity = candidate.entityType.create(
                level,
                net.minecraft.world.entity.EntitySpawnReason.NATURAL
            );
            if (entity == null) {
                return false;
            }

            entity.setPos(spawnPos);

            // Finalize spawn if it's a mob
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(
                    level,
                    level.getCurrentDifficultyAt(candidate.pos),
                    net.minecraft.world.entity.EntitySpawnReason.NATURAL,
                    null
                );
            }

            // Submit spawn intent
            intentMerger.submit(
                EntityIntent.spawn(
                    entity.getId(),
                    spawnPos,
                    entity,
                    "mob_spawner",
                    IntentPriority.withTimestamp(IntentPriority.VANILLA)
                ),
                tickId
            );

            return true;

        } catch (Exception e) {
            LOGGER.warn("Error creating spawn intent: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get spawner statistics
     */
    public SpawnerStats getStats() {
        return new SpawnerStats(
            totalSpawnAttempts.get(),
            successfulSpawns.get(),
            failedSpawns.get(),
            totalSpawnTime.get()
        );
    }

    /**
     * Reset statistics
     */
    public void resetStats() {
        totalSpawnAttempts.set(0);
        successfulSpawns.set(0);
        failedSpawns.set(0);
        totalSpawnTime.set(0);
    }

    private record SpawnCandidate(BlockPos pos, EntityType<?> entityType, MobCategory category) {}

    private record BatchResult(int attempts, int spawned, int failed) {}

    public record SpawnResult(int attempts, int spawned, long timeNanos) {
        public double successRate() {
            return attempts > 0 ? (double) spawned / attempts : 0;
        }
    }

    public record SpawnerStats(
        long totalAttempts,
        long successful,
        long failed,
        long totalTimeNanos
    ) {
        public double successRate() {
            return totalAttempts > 0 ? (double) successful / totalAttempts : 0;
        }

        public double avgTimePerSpawnMs() {
            return successful > 0 ? (totalTimeNanos / successful) / 1_000_000.0 : 0;
        }
    }
}
