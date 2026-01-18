package org.wrd.chrona.async.tracker;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import org.wrd.chrona.util.FastCollections;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Context for batching tracker packets
 * Collects packets during parallel tracking and sends them in batch
 */
public class TrackerContext {
    private final ConcurrentLinkedQueue<PacketEntry> pendingPackets = new ConcurrentLinkedQueue<>();
    private final long tickId;
    private volatile boolean closed = false;

    public TrackerContext(long tickId) {
        this.tickId = tickId;
    }

    /**
     * Queue a packet to be sent to a player
     * Thread-safe for parallel tracking
     */
    public void queuePacket(ServerPlayer player, Packet<?> packet) {
        if (closed) {
            return;
        }
        pendingPackets.add(new PacketEntry(player, packet));
    }

    /**
     * Queue multiple packets for a player
     */
    public void queuePackets(ServerPlayer player, List<Packet<?>> packets) {
        if (closed) {
            return;
        }
        for (Packet<?> packet : packets) {
            pendingPackets.add(new PacketEntry(player, packet));
        }
    }

    /**
     * Flush all pending packets to their respective players
     * Should be called on main thread
     */
    public int flush() {
        closed = true;
        int count = 0;

        PacketEntry entry;
        while ((entry = pendingPackets.poll()) != null) {
            try {
                if (entry.player != null && entry.player.connection != null && entry.packet != null) {
                    entry.player.connection.send(entry.packet);
                    count++;
                }
            } catch (Exception e) {
                // Log but don't fail on packet send errors
            }
        }

        return count;
    }

    /**
     * Get the number of pending packets
     */
    public int getPendingCount() {
        return pendingPackets.size();
    }

    /**
     * Get the tick ID this context is for
     */
    public long getTickId() {
        return tickId;
    }

    /**
     * Check if context is still accepting packets
     */
    public boolean isOpen() {
        return !closed;
    }

    /**
     * Close the context without flushing
     */
    public void discard() {
        closed = true;
        pendingPackets.clear();
    }

    private record PacketEntry(ServerPlayer player, Packet<?> packet) {}
}
