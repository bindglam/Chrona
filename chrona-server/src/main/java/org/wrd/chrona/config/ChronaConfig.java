package org.wrd.chrona.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ChronaConfig {
    private static final Logger LOGGER = LogManager.getLogger(ChronaConfig.class);
    private static FileConfiguration config;

    public static boolean asyncEnabled;
    public static int parallelThreads;
    public static int computeTimeoutMs;
    public static boolean useLockFree;
    public static int computeQueueSize;

    public static boolean pathfindingEnabled;
    public static int pathfindingMaxThreads;
    public static int pathfindingQueueSize;
    public static long pathfindingKeepalive;

    public static int entityBatchSize;
    public static boolean aiParallel;
    public static boolean physicsParallel;
    public static boolean trackingParallel;

    public static boolean redstoneParallel;
    public static int redstoneMaxGraphSize;
    public static boolean redstoneValidateOnCommit;
    public static boolean redstoneSkipOnTruncated;

    public static int chunkGenerationThreads;
    public static int chunkLightingThreads;
    public static int chunkBatchSize;
    public static int chunkIoThreads;
    public static int compressionThreads;

    public static boolean simdEnabled;
    public static boolean virtualThreadsEnabled;
    public static boolean useFastutil;

    public static boolean overlayReads;
    public static boolean queueAsyncAccess;
    public static boolean rejectAsyncWrites;
    public static boolean throwOnAsyncWrite;
    public static boolean warnAsyncAccess;
    public static int asyncViolationThreshold;
    public static boolean disableAsyncOnViolation;

    public static boolean entitySanityCheck;

    public static boolean asyncChunkSend;
    public static boolean asyncMobSpawning;
    public static int mobSpawnBatchSize;
    public static double aiTargetSearchRadius;
    public static int aiAttackCooldownTicks;

    public static org.wrd.chrona.physics.EntityCollisionMode entityCollisionMode;
    public static int maxProjectileLoadsPerTick;
    public static int maxProjectileLoadsPerProjectile;

    public static void load(File configFile) {
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        asyncEnabled = config.getBoolean("chrona.async.enabled", true);
        parallelThreads = config.getInt("chrona.async.parallel-compute.threads", -1);
        if (parallelThreads <= 0) {
            parallelThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        }

        computeTimeoutMs = config.getInt("chrona.async.parallel-compute.timeout-ms", 45);
        useLockFree = config.getBoolean("chrona.async.parallel-compute.use-lock-free", true);
        computeQueueSize = config.getInt("chrona.async.parallel-compute.queue-size", -1);
        if (computeQueueSize <= 0) {
            computeQueueSize = Math.max(2048, parallelThreads * 8192);
        }

        pathfindingEnabled = config.getBoolean("chrona.async.pathfinding.enabled", true);
        pathfindingMaxThreads = config.getInt("chrona.async.pathfinding.max-threads", -1);
        if (pathfindingMaxThreads <= 0) {
            pathfindingMaxThreads = Math.max(Runtime.getRuntime().availableProcessors() / 4, 1);
        }
        pathfindingQueueSize = config.getInt("chrona.async.pathfinding.queue-size", -1);
        if (pathfindingQueueSize <= 0) {
            pathfindingQueueSize = pathfindingMaxThreads * 256;
        }
        pathfindingKeepalive = config.getLong("chrona.async.pathfinding.keepalive-seconds", 60L);

        entityBatchSize = config.getInt("chrona.async.entity.batch-size", 16);
        aiParallel = config.getBoolean("chrona.async.entity.ai-parallel", true);
        physicsParallel = config.getBoolean("chrona.async.entity.physics-parallel", true);
        trackingParallel = config.getBoolean("chrona.async.entity.tracking-parallel", true);

        redstoneParallel = config.getBoolean("chrona.async.redstone.parallel-evaluation", true);
        redstoneMaxGraphSize = config.getInt("chrona.async.redstone.max-graph-size", 10000);
        redstoneValidateOnCommit = config.getBoolean("chrona.async.redstone.validate-on-commit", true);
        redstoneSkipOnTruncated = config.getBoolean("chrona.async.redstone.skip-on-truncated-scan", true);

        chunkGenerationThreads = config.getInt("chrona.async.chunk.generation-threads", 4);
        chunkLightingThreads = config.getInt("chrona.async.chunk.lighting-threads", 2);
        chunkBatchSize = config.getInt("chrona.async.chunk.batch-size", 16);

        chunkIoThreads = config.getInt("chrona.async.io.chunk-io-threads", 4);
        compressionThreads = config.getInt("chrona.async.io.compression-threads", 2);

        simdEnabled = config.getBoolean("chrona.advanced.simd.enabled", true);
        virtualThreadsEnabled = config.getBoolean("chrona.advanced.virtual-threads.enabled", false);
        useFastutil = config.getBoolean("chrona.advanced.collections.use-fastutil", true);

        overlayReads = config.getBoolean("chrona.async.plugin-compat.overlay-reads", true);
        queueAsyncAccess = config.getBoolean("chrona.async.plugin-compat.queue-async-access", true);
        rejectAsyncWrites = config.getBoolean("chrona.async.plugin-compat.reject-async-writes", true);
        throwOnAsyncWrite = config.getBoolean("chrona.async.plugin-compat.throw-on-async-write", false);
        warnAsyncAccess = config.getBoolean("chrona.async.plugin-compat.warn-async-access", true);
        asyncViolationThreshold = config.getInt("chrona.async.plugin-compat.violation-threshold", 50);
        disableAsyncOnViolation = config.getBoolean("chrona.async.plugin-compat.disable-on-violation", false);

        entitySanityCheck = config.getBoolean("chrona.async.entity.sanity-check", true);

        asyncChunkSend = config.getBoolean("chrona.async.chunk.async-send", true);
        asyncMobSpawning = config.getBoolean("chrona.async.entity.async-mob-spawning", true);
        mobSpawnBatchSize = config.getInt("chrona.async.entity.mob-spawn-batch-size", 32);
        aiTargetSearchRadius = config.getDouble("chrona.async.entity.ai-target-search-radius", 16.0);
        aiAttackCooldownTicks = config.getInt("chrona.async.entity.ai-attack-cooldown-ticks", 20);

        // Entity collision optimization (Canvas-inspired)
        String collisionModeStr = config.getString("chrona.physics.entity-collision-mode", "VANILLA");
        try {
            entityCollisionMode = org.wrd.chrona.physics.EntityCollisionMode.valueOf(collisionModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            entityCollisionMode = org.wrd.chrona.physics.EntityCollisionMode.VANILLA;
        }

        maxProjectileLoadsPerTick = config.getInt("chrona.physics.max-projectile-loads-per-tick", 10);
        maxProjectileLoadsPerProjectile = config.getInt("chrona.physics.max-projectile-loads-per-projectile", 10);

        // Validate configuration
        validateConfig();

        LOGGER.info("Chrona configuration loaded successfully");
    }

    private static void validateConfig() {
        boolean hasErrors = false;

        // Validate thread counts
        if (parallelThreads < 1 || parallelThreads > 128) {
            LOGGER.error("Invalid parallel threads: {} (must be 1-128)", parallelThreads);
            parallelThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
            hasErrors = true;
        }

        if (pathfindingMaxThreads < 1 || pathfindingMaxThreads > 64) {
            LOGGER.error("Invalid pathfinding threads: {} (must be 1-64)", pathfindingMaxThreads);
            pathfindingMaxThreads = Math.max(Runtime.getRuntime().availableProcessors() / 4, 1);
            hasErrors = true;
        }

        if (chunkGenerationThreads < 1 || chunkGenerationThreads > 32) {
            LOGGER.error("Invalid chunk generation threads: {} (must be 1-32)", chunkGenerationThreads);
            chunkGenerationThreads = 4;
            hasErrors = true;
        }

        // Validate timeouts
        if (computeTimeoutMs < 1 || computeTimeoutMs > 1000) {
            LOGGER.error("Invalid compute timeout: {}ms (must be 1-1000)", computeTimeoutMs);
            computeTimeoutMs = 45;
            hasErrors = true;
        }

        // Validate batch sizes
        if (entityBatchSize < 1 || entityBatchSize > 256) {
            LOGGER.error("Invalid entity batch size: {} (must be 1-256)", entityBatchSize);
            entityBatchSize = 16;
            hasErrors = true;
        }

        if (redstoneMaxGraphSize < 0 || redstoneMaxGraphSize > 1_000_000) {
            LOGGER.error("Invalid redstone max graph size: {} (must be 0-1000000)", redstoneMaxGraphSize);
            redstoneMaxGraphSize = 10000;
            hasErrors = true;
        }

        if (chunkBatchSize < 1 || chunkBatchSize > 64) {
            LOGGER.error("Invalid chunk batch size: {} (must be 1-64)", chunkBatchSize);
            chunkBatchSize = 16;
            hasErrors = true;
        }

        if (asyncViolationThreshold < 0 || asyncViolationThreshold > 100000) {
            LOGGER.error("Invalid async violation threshold: {} (must be 0-100000)", asyncViolationThreshold);
            asyncViolationThreshold = 50;
            hasErrors = true;
        }

        // Validate queue sizes
        if (pathfindingQueueSize < 16 || pathfindingQueueSize > 65536) {
            LOGGER.error("Invalid pathfinding queue size: {} (must be 16-65536)", pathfindingQueueSize);
            pathfindingQueueSize = pathfindingMaxThreads * 256;
            hasErrors = true;
        }

        // Validate projectile limits
        if (maxProjectileLoadsPerTick < 0 || maxProjectileLoadsPerTick > 1000) {
            LOGGER.error("Invalid max projectile loads per tick: {} (must be 0-1000)", maxProjectileLoadsPerTick);
            maxProjectileLoadsPerTick = 10;
            hasErrors = true;
        }

        if (maxProjectileLoadsPerProjectile < 0 || maxProjectileLoadsPerProjectile > 100) {
            LOGGER.error("Invalid max projectile loads per projectile: {} (must be 0-100)", maxProjectileLoadsPerProjectile);
            maxProjectileLoadsPerProjectile = 10;
            hasErrors = true;
        }

        // Log warnings for potentially problematic configs
        if (parallelThreads > Runtime.getRuntime().availableProcessors()) {
            LOGGER.warn("Parallel threads ({}) exceeds CPU cores ({})",
                parallelThreads, Runtime.getRuntime().availableProcessors());
        }

        if (entityCollisionMode == org.wrd.chrona.physics.EntityCollisionMode.NO_COLLISIONS) {
            LOGGER.warn("Entity collisions are DISABLED - this may break game mechanics");
        }

        if (!asyncEnabled) {
            LOGGER.warn("Chrona async system is DISABLED - server will run in vanilla mode");
        }

        if (hasErrors) {
            LOGGER.warn("Configuration validation found errors - invalid values have been corrected");
        }
    }
}
