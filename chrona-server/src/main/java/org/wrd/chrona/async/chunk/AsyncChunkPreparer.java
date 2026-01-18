package org.wrd.chrona.async.chunk;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.wrd.chrona.async.commit.CommitManager;
import org.wrd.chrona.configuration.ChronaConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles async chunk packet preparation with commit-based ordered sending.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Prepare packets asynchronously (serialization, Anti-Xray)</li>
 *   <li>Queue prepared packets via CommitManager for ordered sending</li>
 *   <li>Ensure batch integrity (START → chunks → FINISHED)</li>
 *   <li>Events fire on main thread after packets are sent</li>
 * </ul>
 */
public class AsyncChunkPreparer {

    private static final Logger LOGGER = LogManager.getLogger("Chrona AsyncChunkPreparer");

    public static final AsyncChunkPreparer INSTANCE = new AsyncChunkPreparer();

    private ThreadPoolExecutor executor;
    private volatile boolean initialized = false;

    // Statistics
    private final AtomicLong totalPrepared = new AtomicLong(0);
    private final AtomicLong totalCommitted = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);

    private AsyncChunkPreparer() {
        // Singleton
    }

    /**
     * Initialize the async chunk preparer.
     */
    public void init(ChronaConfiguration config) {
        if (initialized) {
            LOGGER.warn("AsyncChunkPreparer already initialized");
            return;
        }

        int maxThreads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        int queueSize = maxThreads * 128;

        this.executor = new ThreadPoolExecutor(
                1,
                maxThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new ThreadFactoryBuilder()
                        .setNameFormat("Chrona ChunkPreparer Thread - %d")
                        .setPriority(Thread.NORM_PRIORITY - 1)
                        .setUncaughtExceptionHandler(Util::onThreadException)
                        .build(),
                new ThreadPoolExecutor.CallerRunsPolicy() // Fallback to sync if queue full
        );

        this.initialized = true;
        LOGGER.info("AsyncChunkPreparer initialized with {} max threads", maxThreads);
    }

    /**
     * Prepare and queue a chunk batch for sending.
     *
     * @param connection the player's connection
     * @param level the server level
     * @param chunks the chunks to send
     * @param postSendCallbacks callbacks to run after batch is sent (on main thread)
     */
    public void prepareAndQueueBatch(
            @NotNull ServerGamePacketListenerImpl connection,
            @NotNull ServerLevel level,
            @NotNull List<LevelChunk> chunks,
            @NotNull List<Runnable> postSendCallbacks
    ) {
        if (!initialized || chunks.isEmpty()) {
            return;
        }

        long tickStamp = CommitManager.INSTANCE.getCurrentTick();

        // Submit async preparation task
        executor.execute(() -> {
            try {
                List<Packet<?>> packets = preparePackets(connection, level, chunks);
                totalPrepared.addAndGet(chunks.size());

                // Create callback that runs all post-send callbacks
                Runnable combinedCallback = () -> {
                    for (Runnable callback : postSendCallbacks) {
                        try {
                            callback.run();
                        } catch (Exception e) {
                            LOGGER.warn("Error in chunk send callback", e);
                        }
                    }
                };

                // Queue for main thread sending
                ChunkBatchCommit commit = ChunkBatchCommit.create(
                        connection,
                        tickStamp,
                        packets,
                        combinedCallback
                );

                if (CommitManager.INSTANCE.enqueue(commit)) {
                    totalCommitted.incrementAndGet();
                } else {
                    totalDropped.incrementAndGet();
                }
            } catch (Exception e) {
                LOGGER.error("Error preparing chunk batch", e);
                totalDropped.addAndGet(chunks.size());
            }
        });
    }

    /**
     * Prepare packets for a chunk batch.
     * This runs on a worker thread.
     */
    private List<Packet<?>> preparePackets(
            ServerGamePacketListenerImpl connection,
            ServerLevel level,
            List<LevelChunk> chunks
    ) {
        List<Packet<?>> packets = new ArrayList<>(chunks.size() + 2);

        // 1. Batch start packet
        packets.add(ClientboundChunkBatchStartPacket.INSTANCE);

        // 2. Chunk packets (with Anti-Xray if needed)
        for (LevelChunk chunk : chunks) {
            Packet<?> chunkPacket = prepareChunkPacket(connection, level, chunk);
            if (chunkPacket != null) {
                packets.add(chunkPacket);
            }
        }

        // 3. Batch finished packet
        packets.add(new ClientboundChunkBatchFinishedPacket(chunks.size()));

        return packets;
    }

    /**
     * Prepare a single chunk packet.
     * This captures all necessary data for async preparation.
     */
    private Packet<?> prepareChunkPacket(
            ServerGamePacketListenerImpl connection,
            ServerLevel level,
            LevelChunk chunk
    ) {
        try {
            // Check if Anti-Xray should modify
            boolean shouldModify = level.chunkPacketBlockController.shouldModify(connection.getPlayer(), chunk);

            // Capture block entities snapshot
            BlockEntity[] blockEntities = chunk.blockEntities.values().toArray(new BlockEntity[0]);

            // Capture heightmaps snapshot
            Map<Heightmap.Types, long[]> heightmaps = new ConcurrentHashMap<>();
            chunk.getHeightmaps().forEach(entry -> {
                if (entry.getKey().sendToClient()) {
                    heightmaps.put(entry.getKey(), entry.getValue().getRawData().clone());
                }
            });

            // Create packet with captured data
            return new ClientboundLevelChunkWithLightPacket(
                    chunk,
                    level.getLightEngine(),
                    null,
                    null,
                    shouldModify,
                    blockEntities,
                    heightmaps
            );
        } catch (Exception e) {
            LOGGER.warn("Error preparing chunk packet for {}", chunk.getPos(), e);
            return null;
        }
    }

    /**
     * Check if async chunk preparation is available.
     */
    public boolean isAvailable() {
        return initialized && executor != null && !executor.isShutdown();
    }

    /**
     * Get statistics.
     */
    public Stats getStats() {
        return new Stats(
                totalPrepared.get(),
                totalCommitted.get(),
                totalDropped.get()
        );
    }

    /**
     * Reset statistics.
     */
    public void resetStats() {
        totalPrepared.set(0);
        totalCommitted.set(0);
        totalDropped.set(0);
    }

    /**
     * Shutdown the preparer.
     */
    public void shutdown() {
        if (executor != null) {
            LOGGER.info("Shutting down AsyncChunkPreparer...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Statistics record.
     */
    public record Stats(
            long totalPrepared,
            long totalCommitted,
            long totalDropped
    ) {
        public double getDropRate() {
            long total = totalPrepared + totalDropped;
            return total == 0 ? 0 : (double) totalDropped / total;
        }
    }
}
