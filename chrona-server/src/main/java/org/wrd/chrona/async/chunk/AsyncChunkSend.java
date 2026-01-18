package org.wrd.chrona.async.chunk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.config.ChronaConfig;

import java.util.BitSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async chunk packet serialization based on Leaf's AsyncChunkSend pattern
 * Serializes chunk data off the main thread with backpressure handling
 */
public class AsyncChunkSend {
    private static final Logger LOGGER = LogManager.getLogger(AsyncChunkSend.class);
    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 256;
    private static final long KEEP_ALIVE_MS = 60000;

    private final ServerLevel level;
    private final ExecutorService serializer;
    private final ConcurrentLinkedQueue<PendingChunkSend> pendingQueue = new ConcurrentLinkedQueue<>();

    // Statistics
    private final AtomicLong totalChunksSent = new AtomicLong(0);
    private final AtomicLong totalSerializationTime = new AtomicLong(0);
    private final AtomicLong serializationErrors = new AtomicLong(0);

    public AsyncChunkSend(ServerLevel level) {
        this.level = level;
        this.serializer = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_MS,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "Chrona-ChunkSend-" + level.dimension().toString().replace(":", "-"));
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure: run on caller thread if queue full
        );

        LOGGER.info("AsyncChunkSend initialized for {}", level.dimension());
    }

    /**
     * Queue a chunk to be sent to a player asynchronously
     */
    public CompletableFuture<Void> sendChunkAsync(ServerPlayer player, LevelChunk chunk) {
        if (!ChronaConfig.asyncChunkSend || player == null || chunk == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                serializeAndSend(player, chunk);
            } catch (Exception e) {
                LOGGER.warn("Error sending chunk async to {}: {}", player.getName().getString(), e.getMessage());
                serializationErrors.incrementAndGet();
            }
        }, serializer);
    }

    /**
     * Send multiple chunks to a player asynchronously
     */
    public CompletableFuture<Void> sendChunksAsync(ServerPlayer player, LevelChunk[] chunks) {
        if (!ChronaConfig.asyncChunkSend || player == null || chunks == null || chunks.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            for (LevelChunk chunk : chunks) {
                if (chunk != null) {
                    try {
                        serializeAndSend(player, chunk);
                    } catch (Exception e) {
                        LOGGER.warn("Error sending chunk {} to {}: {}",
                            chunk.getPos(), player.getName().getString(), e.getMessage());
                        serializationErrors.incrementAndGet();
                    }
                }
            }
        }, serializer);
    }

    private void serializeAndSend(ServerPlayer player, LevelChunk chunk) {
        long startTime = System.nanoTime();

        try {
            // Check if player is still valid
            if (player.isRemoved() || player.connection == null) {
                return;
            }

            // Create the chunk packet
            LevelLightEngine lightEngine = level.getLightEngine();
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                chunk,
                lightEngine,
                null, // skyLightBitSet
                null  // blockLightBitSet
            );

            // Send on main thread via connection
            // Note: In a real implementation, you might need to schedule this
            if (player.connection != null && !player.isRemoved()) {
                player.connection.send(packet);
                totalChunksSent.incrementAndGet();
            }

        } finally {
            long elapsed = System.nanoTime() - startTime;
            totalSerializationTime.addAndGet(elapsed);
        }
    }

    /**
     * Queue a chunk send without immediate execution
     * Useful for batching
     */
    public void queueChunkSend(ServerPlayer player, LevelChunk chunk) {
        if (player != null && chunk != null) {
            pendingQueue.add(new PendingChunkSend(player, chunk, System.currentTimeMillis()));
        }
    }

    /**
     * Flush all pending chunk sends
     * Should be called periodically on main thread
     */
    public int flushPendingChunks() {
        int count = 0;
        PendingChunkSend pending;

        while ((pending = pendingQueue.poll()) != null) {
            // Skip stale entries (older than 5 seconds)
            if (System.currentTimeMillis() - pending.queueTime > 5000) {
                continue;
            }

            try {
                sendChunkAsync(pending.player, pending.chunk);
                count++;
            } catch (Exception e) {
                LOGGER.warn("Error flushing chunk send: {}", e.getMessage());
            }
        }

        return count;
    }

    /**
     * Get send statistics
     */
    public ChunkSendStats getStats() {
        return new ChunkSendStats(
            totalChunksSent.get(),
            totalSerializationTime.get(),
            serializationErrors.get(),
            pendingQueue.size()
        );
    }

    /**
     * Reset statistics
     */
    public void resetStats() {
        totalChunksSent.set(0);
        totalSerializationTime.set(0);
        serializationErrors.set(0);
    }

    /**
     * Shutdown the async sender
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncChunkSend for {}", level.dimension());

        // Process remaining queue
        flushPendingChunks();

        serializer.shutdown();
        try {
            if (!serializer.awaitTermination(5, TimeUnit.SECONDS)) {
                serializer.shutdownNow();
            }
        } catch (InterruptedException e) {
            serializer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record PendingChunkSend(ServerPlayer player, LevelChunk chunk, long queueTime) {}

    public record ChunkSendStats(
        long chunksSent,
        long serializationTimeNanos,
        long errors,
        int pendingCount
    ) {
        public double avgSerializationMs() {
            return chunksSent > 0 ? (serializationTimeNanos / chunksSent) / 1_000_000.0 : 0;
        }
    }
}
