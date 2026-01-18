package org.wrd.chrona.async.pool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lock-free object pool for reducing GC pressure in async operations.
 *
 * <p>Uses a simple ring buffer with atomic operations for thread-safety.
 * Objects are reset via a consumer before being returned to the pool.
 *
 * @param <T> the type of pooled objects
 */
public class ObjectPool<T> {

    private final Object[] pool;
    private final int capacity;
    private final int mask;
    private final Supplier<T> factory;
    private final Consumer<T> resetter;

    private final AtomicInteger head = new AtomicInteger(0);
    private final AtomicInteger tail = new AtomicInteger(0);
    private final AtomicInteger size = new AtomicInteger(0);

    // Statistics
    private final AtomicInteger hits = new AtomicInteger(0);
    private final AtomicInteger misses = new AtomicInteger(0);

    /**
     * Creates a new object pool.
     *
     * @param capacity maximum number of objects to pool (will be rounded to power of 2)
     * @param factory  supplier to create new objects when pool is empty
     * @param resetter consumer to reset objects before returning to pool (can be null)
     */
    public ObjectPool(int capacity, @NotNull Supplier<T> factory, @Nullable Consumer<T> resetter) {
        // Round up to power of 2
        int actualCapacity = 1;
        while (actualCapacity < capacity) {
            actualCapacity <<= 1;
        }

        this.capacity = actualCapacity;
        this.mask = actualCapacity - 1;
        this.pool = new Object[actualCapacity];
        this.factory = factory;
        this.resetter = resetter;
    }

    /**
     * Creates a pool without a resetter.
     */
    public ObjectPool(int capacity, @NotNull Supplier<T> factory) {
        this(capacity, factory, null);
    }

    /**
     * Acquire an object from the pool, or create a new one if empty.
     *
     * @return an object ready for use
     */
    @SuppressWarnings("unchecked")
    public @NotNull T acquire() {
        // Try to get from pool
        int currentSize;
        while ((currentSize = size.get()) > 0) {
            if (size.compareAndSet(currentSize, currentSize - 1)) {
                int index = head.getAndIncrement() & mask;
                T obj = (T) pool[index];
                pool[index] = null;
                if (obj != null) {
                    hits.incrementAndGet();
                    return obj;
                }
                // Slot was empty (race condition), continue trying
                size.incrementAndGet();
            }
        }

        // Pool empty, create new
        misses.incrementAndGet();
        return factory.get();
    }

    /**
     * Release an object back to the pool.
     * If the pool is full, the object is discarded.
     *
     * @param obj the object to release
     */
    public void release(@NotNull T obj) {
        // Reset the object
        if (resetter != null) {
            try {
                resetter.accept(obj);
            } catch (Exception e) {
                // Failed to reset, don't pool it
                return;
            }
        }

        // Try to add to pool
        int currentSize;
        while ((currentSize = size.get()) < capacity) {
            if (size.compareAndSet(currentSize, currentSize + 1)) {
                int index = tail.getAndIncrement() & mask;
                pool[index] = obj;
                return;
            }
        }
        // Pool full, discard
    }

    /**
     * Pre-fill the pool with objects.
     *
     * @param count number of objects to create
     */
    public void prefill(int count) {
        count = Math.min(count, capacity);
        for (int i = 0; i < count; i++) {
            release(factory.get());
        }
    }

    /**
     * Clear the pool.
     */
    public void clear() {
        while (size.get() > 0) {
            if (size.decrementAndGet() >= 0) {
                int index = head.getAndIncrement() & mask;
                pool[index] = null;
            } else {
                size.incrementAndGet();
                break;
            }
        }
    }

    /**
     * Get current pool size.
     */
    public int size() {
        return Math.max(0, size.get());
    }

    /**
     * Get pool capacity.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Get cache hit count.
     */
    public int getHits() {
        return hits.get();
    }

    /**
     * Get cache miss count (new objects created).
     */
    public int getMisses() {
        return misses.get();
    }

    /**
     * Get hit ratio.
     */
    public double getHitRatio() {
        int h = hits.get();
        int m = misses.get();
        int total = h + m;
        return total == 0 ? 0.0 : (double) h / total;
    }

    /**
     * Reset statistics.
     */
    public void resetStats() {
        hits.set(0);
        misses.set(0);
    }
}
