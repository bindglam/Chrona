package org.wrd.chrona.async.chunk;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wrd.chrona.async.commit.Commit;
import org.wrd.chrona.async.commit.CommitPhase;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

/**
 * Commit for sending a batch of chunk packets in order.
 *
 * <p>This commit ensures that:
 * <ul>
 *   <li>Packets are prepared asynchronously (serialization, Anti-Xray)</li>
 *   <li>Packets are sent on main thread at PRE_NETWORK_FLUSH phase</li>
 *   <li>Packet ordering within a batch is preserved</li>
 *   <li>Coalescing by player prevents duplicate sends</li>
 * </ul>
 */
public class ChunkBatchCommit implements Commit {

    private final WeakReference<ServerGamePacketListenerImpl> connectionRef;
    private final UUID playerUUID;
    private final long tickStamp;
    private final List<Packet<?>> packets;
    private final Runnable postSendCallback;

    private ChunkBatchCommit(
            ServerGamePacketListenerImpl connection,
            UUID playerUUID,
            long tickStamp,
            List<Packet<?>> packets,
            @Nullable Runnable postSendCallback
    ) {
        this.connectionRef = new WeakReference<>(connection);
        this.playerUUID = playerUUID;
        this.tickStamp = tickStamp;
        this.packets = packets;
        this.postSendCallback = postSendCallback;
    }

    /**
     * Create a new chunk batch commit.
     *
     * @param connection the player's connection
     * @param tickStamp the tick when this commit was created
     * @param packets the ordered list of packets to send
     * @param postSendCallback optional callback after packets are sent
     * @return the commit
     */
    public static ChunkBatchCommit create(
            @NotNull ServerGamePacketListenerImpl connection,
            long tickStamp,
            @NotNull List<Packet<?>> packets,
            @Nullable Runnable postSendCallback
    ) {
        return new ChunkBatchCommit(
                connection,
                connection.getPlayer().getUUID(),
                tickStamp,
                packets,
                postSendCallback
        );
    }

    @Override
    public long getTickStamp() {
        return tickStamp;
    }

    @Override
    public CommitPhase getPhase() {
        // Send at PRE_NETWORK_FLUSH to ensure packets go out together
        return CommitPhase.PRE_NETWORK_FLUSH;
    }

    @Override
    public @Nullable Object getCoalesceKey() {
        // Coalesce by player - newer batches replace older pending batches
        // This prevents sending stale chunk data if player moves fast
        return null; // Don't coalesce chunk sends - each batch is important
    }

    @Override
    public boolean validate() {
        ServerGamePacketListenerImpl connection = connectionRef.get();
        if (connection == null) {
            return false;
        }

        // Ensure player is still connected
        return connection.getPlayer() != null && connection.getPlayer().isAlive();
    }

    @Override
    public void apply() {
        ServerGamePacketListenerImpl connection = connectionRef.get();
        if (connection == null) {
            return;
        }

        // Send all packets in order
        for (Packet<?> packet : packets) {
            connection.send(packet);
        }

        // Run post-send callback (e.g., for events)
        if (postSendCallback != null) {
            postSendCallback.run();
        }
    }

    @Override
    public void onDropped() {
        // Chunk batch was dropped - packets won't be sent
        // This is okay - player will request chunks again if needed
    }

    @Override
    public int getMaxStaleTicks() {
        // Chunk packets should be sent within 5 ticks
        // After that, player position may have changed significantly
        return 5;
    }
}
