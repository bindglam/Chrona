package org.wrd.chrona.tick.phase;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.util.FastCollections;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Merges intents from parallel compute phase with conflict resolution
 * Thread-safe and optimized for high throughput
 */
public class IntentMerger {
    private static final Logger LOGGER = LogManager.getLogger(IntentMerger.class);
    private static final int INITIAL_CAPACITY = 256;
    private static final int WARN_THRESHOLD = 10000;

    private final ObjectArrayList<Intent> allIntents = FastCollections.newObjectList(INITIAL_CAPACITY);
    private final Long2ObjectMap<ObjectArrayList<BlockIntent>> blockIntentsByPos = FastCollections.newLong2ObjectMap(128);
    private final Int2ObjectMap<ObjectArrayList<EntityIntent>> entityIntentsById = FastCollections.newInt2ObjectMap(64);
    private final Long2ObjectMap<ObjectArrayList<ChunkIntent>> chunkIntentsByPos = FastCollections.newLong2ObjectMap(32);
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long activeTick = -1;
    private volatile boolean accepting = false;

    // Statistics
    private final AtomicLong totalIntentsSubmitted = new AtomicLong(0);
    private final AtomicLong totalIntentsMerged = new AtomicLong(0);
    private final AtomicLong totalConflicts = new AtomicLong(0);

    /**
     * Begin accepting intents for a specific tick.
     */
    public void beginTick(long tickId) {
        lock.lock();
        try {
            activeTick = tickId;
            accepting = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stop accepting intents for a specific tick.
     */
    public void endTick(long tickId) {
        lock.lock();
        try {
            if (activeTick == tickId) {
                accepting = false;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isAccepting() {
        return accepting;
    }

    public long getActiveTick() {
        return activeTick;
    }

    /**
     * Submit an intent to be merged for the currently active tick.
     * Thread-safe and can be called from parallel compute threads.
     */
    public boolean submit(Intent intent) {
        return submit(intent, activeTick);
    }

    /**
     * Submit an intent to be merged for a specific tick.
     * Thread-safe and can be called from parallel compute threads.
     */
    public boolean submit(Intent intent, long tickId) {
        if (intent == null) {
            LOGGER.warn("Attempted to submit null intent");
            return false;
        }

        if (!accepting || tickId != activeTick) {
            return false;
        }

        lock.lock();
        try {
            if (!accepting || tickId != activeTick) {
                return false;
            }

            totalIntentsSubmitted.incrementAndGet();
            allIntents.add(intent);

            if (intent instanceof BlockIntent bi) {
                blockIntentsByPos
                    .computeIfAbsent(bi.pos().asLong(), k -> FastCollections.newObjectList())
                    .add(bi);
            } else if (intent instanceof EntityIntent ei) {
                entityIntentsById
                    .computeIfAbsent(ei.entityId(), k -> FastCollections.newObjectList())
                    .add(ei);
            } else if (intent instanceof ChunkIntent ci) {
                chunkIntentsByPos
                    .computeIfAbsent(ci.packedPos(), k -> FastCollections.newObjectList())
                    .add(ci);
            }

            // Warn if too many intents
            if (allIntents.size() > WARN_THRESHOLD && allIntents.size() % WARN_THRESHOLD == 0) {
                LOGGER.warn("High intent count: {} intents pending merge", allIntents.size());
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Merge all submitted intents, resolving conflicts
     * Returns a deterministic, priority-sorted list of intents to apply
     */
    public List<Intent> merge() {
        lock.lock();
        try {
            long startTime = System.nanoTime();
            ObjectArrayList<Intent> merged = FastCollections.newObjectList(allIntents.size());
            long conflictCount = 0;

            // Merge block intents
            for (ObjectArrayList<BlockIntent> conflicts : blockIntentsByPos.values()) {
                if (conflicts.size() == 1) {
                    merged.add(conflicts.get(0));
                } else {
                    conflictCount += conflicts.size() - 1;
                    conflicts.sort(Comparator.comparingLong(BlockIntent::priority).reversed());
                    merged.add(conflicts.get(0)); // Highest priority wins
                }
            }

            // Merge entity intents
            for (ObjectArrayList<EntityIntent> conflicts : entityIntentsById.values()) {
                if (conflicts.size() == 1) {
                    merged.addAll(conflicts);
                } else {
                    conflictCount += resolveEntityIntentConflicts(conflicts, merged);
                }
            }

            // Merge chunk intents
            for (ObjectArrayList<ChunkIntent> conflicts : chunkIntentsByPos.values()) {
                if (conflicts.size() == 1) {
                    merged.add(conflicts.get(0));
                } else {
                    conflictCount += resolveChunkIntentConflicts(conflicts, merged);
                }
            }

            // Sort by priority (deterministic ordering)
            merged.sort(Comparator.comparingLong(Intent::getPriority).reversed());

            totalIntentsMerged.addAndGet(merged.size());
            totalConflicts.addAndGet(conflictCount);

            long mergeTime = System.nanoTime() - startTime;
            if (mergeTime > 1_000_000) { // > 1ms
                LOGGER.debug("Intent merge took {}ms for {} intents ({} conflicts)",
                    String.format("%.2f", mergeTime / 1_000_000.0),
                    merged.size(),
                    conflictCount);
            }

            return merged;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resolve conflicts between entity intents for the same entity
     * Returns number of conflicts resolved
     */
    private long resolveEntityIntentConflicts(ObjectArrayList<EntityIntent> conflicts, ObjectArrayList<Intent> merged) {
        ObjectArrayList<EntityIntent> moves = FastCollections.newObjectList();
        ObjectArrayList<EntityIntent> damages = FastCollections.newObjectList();
        ObjectArrayList<EntityIntent> others = FastCollections.newObjectList();

        // Separate by intent type
        for (EntityIntent ei : conflicts) {
            switch (ei.type()) {
                case ENTITY_MOVE -> moves.add(ei);
                case DAMAGE -> damages.add(ei);
                default -> others.add(ei);
            }
        }

        long conflictCount = 0;

        // Only keep highest priority move
        if (!moves.isEmpty()) {
            if (moves.size() > 1) {
                moves.sort(Comparator.comparingLong(EntityIntent::priority).reversed());
                conflictCount += moves.size() - 1;
            }
            merged.add(moves.get(0));
        }

        // Keep all damage intents (can stack)
        merged.addAll(damages);

        // Keep all other intents
        merged.addAll(others);

        return conflictCount;
    }

    /**
     * Resolve conflicts between chunk intents for the same chunk position
     * Returns number of conflicts resolved
     */
    private long resolveChunkIntentConflicts(ObjectArrayList<ChunkIntent> conflicts, ObjectArrayList<Intent> merged) {
        ObjectArrayList<ChunkIntent> genIntents = FastCollections.newObjectList();
        ObjectArrayList<ChunkIntent> structureIntents = FastCollections.newObjectList();

        // Separate by intent type
        for (ChunkIntent ci : conflicts) {
            switch (ci.type()) {
                case CHUNK_GEN -> genIntents.add(ci);
                case STRUCTURE_PLACE -> structureIntents.add(ci);
                default -> merged.add(ci);
            }
        }

        long conflictCount = 0;

        // Only keep highest priority gen intent
        if (!genIntents.isEmpty()) {
            if (genIntents.size() > 1) {
                genIntents.sort(Comparator.comparingLong(ChunkIntent::priority).reversed());
                conflictCount += genIntents.size() - 1;
            }
            merged.add(genIntents.get(0));
        }

        // Keep all structure intents (can layer)
        merged.addAll(structureIntents);

        return conflictCount;
    }

    /**
     * Clear all pending intents
     * Should be called after merge and commit
     */
    public void clear() {
        lock.lock();
        try {
            allIntents.clear();
            blockIntentsByPos.clear();
            entityIntentsById.clear();
            chunkIntentsByPos.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get merger statistics
     */
    public MergerStats getStats() {
        return new MergerStats(
            totalIntentsSubmitted.get(),
            totalIntentsMerged.get(),
            totalConflicts.get(),
            allIntents.size()
        );
    }

    /**
     * Reset statistics
     */
    public void resetStats() {
        totalIntentsSubmitted.set(0);
        totalIntentsMerged.set(0);
        totalConflicts.set(0);
    }

    public record MergerStats(
        long submitted,
        long merged,
        long conflicts,
        int pending
    ) {
        public double conflictRate() {
            return submitted > 0 ? (double) conflicts / submitted : 0.0;
        }

        public double mergeRate() {
            return submitted > 0 ? (double) merged / submitted : 0.0;
        }
    }
}
