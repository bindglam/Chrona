package org.wrd.chrona.async.entity.path;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.wrd.chrona.configuration.ChronaConfiguration;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache for NodeEvaluator instances with TTL and max size limits.
 * Thread-safe implementation for async pathfinding.
 */
public class NodeEvaluatorCache {

    private static final Map<NodeEvaluatorFeatures, CachedQueue> threadLocalNodeEvaluators = new ConcurrentHashMap<>();
    private static final Map<NodeEvaluator, CacheEntry> nodeEvaluatorToEntry = new ConcurrentHashMap<>();

    // Configuration values (set during init)
    private static volatile int maxCacheSize = 1024;
    private static volatile long expiryMs = 5000L;

    // Cache statistics
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);

    /**
     * Initialize cache with configuration values.
     */
    public static void init(ChronaConfiguration config) {
        maxCacheSize = config.optimization.async.pathfinding.cache.maxSize.getValue();
        // YAML parser may return Integer for numeric values, so convert safely
        Number expiryValue = (Number) config.optimization.async.pathfinding.cache.expiryMs.getValue();
        expiryMs = expiryValue.longValue();
    }

    private static @NotNull CachedQueue getQueueForFeatures(@NotNull NodeEvaluatorFeatures nodeEvaluatorFeatures) {
        return threadLocalNodeEvaluators.computeIfAbsent(nodeEvaluatorFeatures, key -> new CachedQueue());
    }

    public static @NotNull NodeEvaluator takeNodeEvaluator(@NotNull NodeEvaluatorGenerator generator, @NotNull NodeEvaluator localNodeEvaluator) {
        final NodeEvaluatorFeatures nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(localNodeEvaluator);
        final CachedQueue cachedQueue = getQueueForFeatures(nodeEvaluatorFeatures);

        NodeEvaluator nodeEvaluator = cachedQueue.poll();

        if (nodeEvaluator == null) {
            nodeEvaluator = generator.generate(nodeEvaluatorFeatures);
            cacheMisses.incrementAndGet();
        } else {
            cacheHits.incrementAndGet();
        }

        nodeEvaluatorToEntry.put(nodeEvaluator, new CacheEntry(generator, System.currentTimeMillis()));

        return nodeEvaluator;
    }

    public static void returnNodeEvaluator(@NotNull NodeEvaluator nodeEvaluator) {
        final CacheEntry entry = nodeEvaluatorToEntry.remove(nodeEvaluator);
        Validate.notNull(entry, "NodeEvaluator already returned");

        final NodeEvaluatorFeatures nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(nodeEvaluator);
        final CachedQueue cachedQueue = getQueueForFeatures(nodeEvaluatorFeatures);

        // Check if cache is full or entry is expired
        if (cachedQueue.size() >= maxCacheSize) {
            // Cache full, don't return to pool
            return;
        }

        // Check TTL
        if (expiryMs > 0 && (System.currentTimeMillis() - entry.createdAt) > expiryMs) {
            // Entry expired, don't return to pool
            return;
        }

        cachedQueue.offer(nodeEvaluator);
    }

    public static void removeNodeEvaluator(@NotNull NodeEvaluator nodeEvaluator) {
        nodeEvaluatorToEntry.remove(nodeEvaluator);
    }

    /**
     * Clean up expired entries from all caches.
     * Should be called periodically (e.g., every tick or every few seconds).
     */
    public static void cleanupExpired() {
        if (expiryMs <= 0) return;

        final long now = System.currentTimeMillis();
        final long threshold = now - expiryMs;

        nodeEvaluatorToEntry.entrySet().removeIf(entry -> entry.getValue().createdAt < threshold);
    }

    /**
     * Clear all caches.
     */
    public static void clearAll() {
        threadLocalNodeEvaluators.clear();
        nodeEvaluatorToEntry.clear();
    }

    /**
     * Get cache hit count.
     */
    public static long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Get cache miss count.
     */
    public static long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Get cache hit ratio.
     */
    public static double getHitRatio() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /**
     * Reset cache statistics.
     */
    public static void resetStats() {
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    /**
     * Get total cached entries count.
     */
    public static int getTotalCachedCount() {
        return threadLocalNodeEvaluators.values().stream()
            .mapToInt(CachedQueue::size)
            .sum();
    }

    private record CacheEntry(NodeEvaluatorGenerator generator, long createdAt) {}

    /**
     * Thread-safe queue wrapper with size tracking.
     */
    private static class CachedQueue {
        private final MultiThreadedQueue<NodeEvaluator> queue = new MultiThreadedQueue<>();
        private final AtomicInteger size = new AtomicInteger(0);

        public NodeEvaluator poll() {
            NodeEvaluator result = queue.poll();
            if (result != null) {
                size.decrementAndGet();
            }
            return result;
        }

        public boolean offer(NodeEvaluator evaluator) {
            if (queue.offer(evaluator)) {
                size.incrementAndGet();
                return true;
            }
            return false;
        }

        public int size() {
            return size.get();
        }
    }
}
