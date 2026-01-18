package org.wrd.chrona;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.async.entity.path.AsyncPathProcessor;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.simd.SIMDDetection;
import org.wrd.chrona.tick.WorldTickManager;
import org.wrd.chrona.chunk.parallel.ChunkIntentPipeline;
import org.wrd.chrona.tick.parallel.AIIntentComputer;
import org.wrd.chrona.tick.parallel.EntityIntentPlannerRegistry;
import org.wrd.chrona.tick.parallel.EntitySanityIntentComputer;
import org.wrd.chrona.tick.parallel.PhysicsIntentComputer;
import org.wrd.chrona.util.VirtualThreads;

import java.io.File;

public class ChronaBootstrap {
    private static final Logger LOGGER = LogManager.getLogger("Chrona");
    private static boolean initialized = false;

    public static void init(File configFile) {
        if (initialized) {
            LOGGER.warn("Chrona already initialized");
            return;
        }

        LOGGER.info("=== Chrona Async Server ===");
        LOGGER.info("Initializing high-performance async tick system");

        ChronaConfig.load(configFile);

        if (ChronaConfig.simdEnabled) {
            SIMDDetection.init();
        }

        if (ChronaConfig.virtualThreadsEnabled) {
            if (VirtualThreads.isAvailable()) {
                LOGGER.info("Virtual threads enabled");
            } else {
                LOGGER.warn("Virtual threads requested but not available (Java 21+ required)");
            }
        }

        AsyncPathProcessor.init(null);

        // Register intent computers
        EntityIntentPlannerRegistry.register(new EntitySanityIntentComputer());
        EntityIntentPlannerRegistry.register(new PhysicsIntentComputer());
        EntityIntentPlannerRegistry.register(new AIIntentComputer());

        LOGGER.info("Registered {} entity intent computers", EntityIntentPlannerRegistry.getComputerCount());
        LOGGER.info("Registered {} chunk intent computers", ChunkIntentPipeline.getComputerCount());

        WorldTickManager.init(
            ChronaConfig.parallelThreads,
            ChronaConfig.chunkGenerationThreads,
            ChronaConfig.chunkIoThreads,
            ChronaConfig.chunkLightingThreads
        );

        initialized = true;
        LOGGER.info("Chrona initialization complete");
        LOGGER.info("  Parallel threads: {}", ChronaConfig.parallelThreads);
        LOGGER.info("  Pathfinding threads: {}", ChronaConfig.pathfindingMaxThreads);
        LOGGER.info("  Chunk gen threads: {}", ChronaConfig.chunkGenerationThreads);
        LOGGER.info("  Lock-free executor: {}", ChronaConfig.useLockFree);
        LOGGER.info("  SIMD: {}", SIMDDetection.isEnabled());
        LOGGER.info("  Virtual threads: {}", VirtualThreads.isAvailable() && ChronaConfig.virtualThreadsEnabled);
    }

    public static void shutdown() {
        if (!initialized) return;

        LOGGER.info("Shutting down Chrona");
        WorldTickManager.getInstance().shutdown();
        AsyncPathProcessor.shutdown();
        initialized = false;
    }
}
