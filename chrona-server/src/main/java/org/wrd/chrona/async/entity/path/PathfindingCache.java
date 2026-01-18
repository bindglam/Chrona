package org.wrd.chrona.async.entity.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches pathfinding results to avoid redundant calculations
 * Similar to Leaf's approach but with Chrona's async integration
 */
public class PathfindingCache {
    private static final int DEFAULT_MAX_CACHE_SIZE = 1024;
    private static final long DEFAULT_CACHE_EXPIRY_MS = 5000; // 5 seconds

    private final int maxCacheSize;
    private final long cacheExpiryMs;

    private final Map<PathKey, CachedPath> cache = new ConcurrentHashMap<>();
    private long lastCleanup = System.currentTimeMillis();

    public PathfindingCache() {
        this(DEFAULT_MAX_CACHE_SIZE, DEFAULT_CACHE_EXPIRY_MS);
    }

    public PathfindingCache(int maxCacheSize, long cacheExpiryMs) {
        this.maxCacheSize = Math.max(1, maxCacheSize);
        this.cacheExpiryMs = Math.max(1L, cacheExpiryMs);
    }

    public Path getCachedPath(int entityId, BlockPos start, BlockPos target) {
        PathKey key = new PathKey(entityId, start, target);
        CachedPath cached = cache.get(key);

        if (cached != null && !cached.isExpired()) {
            return copyPath(cached.path);
        }

        // Cleanup old entries periodically
        if (System.currentTimeMillis() - lastCleanup > cacheExpiryMs) {
            cleanup();
        }

        return null;
    }

    public void cachePath(int entityId, BlockPos start, BlockPos target, Path path) {
        if (path instanceof AsyncPath asyncPath && !asyncPath.isProcessed()) {
            return;
        }

        if (cache.size() >= maxCacheSize) {
            cleanup();
            trimToSize();
        }

        Path copy = copyPath(path);
        if (copy == null) {
            return;
        }

        PathKey key = new PathKey(entityId, start, target);
        cache.put(key, new CachedPath(copy, System.currentTimeMillis(), cacheExpiryMs));
    }

    public void invalidate(int entityId) {
        cache.entrySet().removeIf(entry -> entry.getKey().entityId == entityId);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        lastCleanup = now;
    }

    private void trimToSize() {
        int overflow = cache.size() - maxCacheSize;
        if (overflow <= 0) {
            return;
        }

        Iterator<PathKey> iterator = cache.keySet().iterator();
        while (iterator.hasNext() && overflow-- > 0) {
            cache.remove(iterator.next());
        }
    }

    private Path copyPath(Path path) {
        if (path == null) {
            return null;
        }

        if (path.getTarget() == null) {
            return null;
        }

        return new Path(new ArrayList<>(path.nodes), path.getTarget(), path.canReach());
    }

    public void clear() {
        cache.clear();
    }

    private static class PathKey {
        final int entityId;
        final BlockPos start;
        final BlockPos target;
        final int hash;

        PathKey(int entityId, BlockPos start, BlockPos target) {
            this.entityId = entityId;
            this.start = start;
            this.target = target;
            this.hash = Objects.hash(entityId, start, target);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathKey other)) return false;
            return entityId == other.entityId &&
                   start.equals(other.start) &&
                   target.equals(other.target);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static class CachedPath {
        final Path path;
        final long timestamp;
        final long expiryMs;

        CachedPath(Path path, long timestamp, long expiryMs) {
            this.path = path;
            this.timestamp = timestamp;
            this.expiryMs = expiryMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > expiryMs;
        }
    }
}
