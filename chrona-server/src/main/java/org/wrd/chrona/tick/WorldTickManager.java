package org.wrd.chrona.tick;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class WorldTickManager {
    private static final Logger LOGGER = LogManager.getLogger(WorldTickManager.class);
    private static WorldTickManager instance;

    private final Map<ServerLevel, AsyncTickExecutor> executors = new Object2ObjectOpenHashMap<>();
    private final int parallelThreads;
    private final int genThreads;
    private final int ioThreads;
    private final int lightThreads;

    // Chrona - Thread-local flag to prevent infinite recursion
    // When true, vanilla tick is running and should not redirect back to Chrona
    private static final ThreadLocal<Boolean> IN_VANILLA_TICK = ThreadLocal.withInitial(() -> false);

    private WorldTickManager(int parallelThreads, int genThreads, int ioThreads, int lightThreads) {
        this.parallelThreads = parallelThreads;
        this.genThreads = genThreads;
        this.ioThreads = ioThreads;
        this.lightThreads = lightThreads;
    }

    public static void init(int parallelThreads, int genThreads, int ioThreads, int lightThreads) {
        if (instance == null) {
            instance = new WorldTickManager(parallelThreads, genThreads, ioThreads, lightThreads);
            LOGGER.info("Initialized WorldTickManager: compute={}, gen={}, io={}, light={}",
                parallelThreads, genThreads, ioThreads, lightThreads);
        }
    }

    public static WorldTickManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("WorldTickManager not initialized");
        }
        return instance;
    }

    public AsyncTickExecutor getOrCreateExecutor(ServerLevel level) {
        return executors.computeIfAbsent(level, l -> {
            LOGGER.info("Creating AsyncTickExecutor for world: {}", l.dimension());
            return new AsyncTickExecutor(l, parallelThreads, genThreads, ioThreads, lightThreads);
        });
    }

    public void tickWorld(ServerLevel level, java.util.function.BooleanSupplier hasTimeLeft) {
        AsyncTickExecutor executor = getOrCreateExecutor(level);
        executor.executeTick(hasTimeLeft);
    }

    /**
     * Check if currently inside vanilla tick (to prevent recursion)
     */
    public static boolean isInVanillaTick() {
        return IN_VANILLA_TICK.get();
    }

    /**
     * Set vanilla tick flag (called by AsyncTickExecutor)
     */
    public static void setInVanillaTick(boolean value) {
        IN_VANILLA_TICK.set(value);
    }

    public AsyncTickExecutor getExecutor(ServerLevel level) {
        return executors.get(level);
    }

    public AsyncTickExecutor getExecutor() {
        // For compatibility - returns the first executor if only one world
        if (executors.size() == 1) {
            return executors.values().iterator().next();
        }
        return null;
    }

    public void shutdown() {
        LOGGER.info("Shutting down WorldTickManager");
        for (AsyncTickExecutor executor : executors.values()) {
            executor.shutdown();
        }
        executors.clear();
    }
}
