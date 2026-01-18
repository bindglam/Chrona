package org.wrd.chrona.tick.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.tick.overlay.OverlayWorldView;
import org.wrd.chrona.tick.phase.BlockIntent;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.PluginPhaseExecutor;
import org.wrd.chrona.tick.safety.SafetyValidator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BukkitCompatLayer {
    private static final ThreadLocal<IntentMerger> INTENT_MERGER = new ThreadLocal<>();
    private static final AtomicBoolean WARNED_ASYNC = new AtomicBoolean(false);
    private static final AtomicBoolean WARNED_ASYNC_READ = new AtomicBoolean(false);
    private static final AtomicInteger ASYNC_VIOLATIONS = new AtomicInteger(0);
    private static volatile long ACTIVE_TICK = -1;

    public static void setIntentMerger(IntentMerger merger) {
        INTENT_MERGER.set(merger);
    }

    public static void beginTick(long tickId) {
        ACTIVE_TICK = tickId;
        ASYNC_VIOLATIONS.set(0);
    }

    public static void endTick(long tickId) {
        if (ACTIVE_TICK != tickId) {
            return;
        }

        int violations = ASYNC_VIOLATIONS.get();
        if (violations >= ChronaConfig.asyncViolationThreshold && ChronaConfig.disableAsyncOnViolation) {
            ChronaConfig.asyncEnabled = false;
            org.apache.logging.log4j.LogManager.getLogger(BukkitCompatLayer.class)
                .warn("Disabling Chrona async after {} async world access violations", violations);
        }
    }

    public static void clearIntentMerger() {
        INTENT_MERGER.remove();
    }

    public static BlockState interceptGetBlock(Level level, BlockPos pos, BlockState original) {
        boolean isMainThread = org.bukkit.Bukkit.isPrimaryThread();
        boolean isPluginPhase = PluginPhaseExecutor.isPluginThread();
        if (!isMainThread && !isPluginPhase) {
            recordAsyncViolation("getBlock");
            if (ChronaConfig.warnAsyncAccess && WARNED_ASYNC_READ.compareAndSet(false, true)) {
                org.apache.logging.log4j.LogManager.getLogger(BukkitCompatLayer.class)
                    .warn("Async getBlock detected - reads are unsafe off the main thread");
            }
            return original;
        }

        if (!isPluginPhase || !ChronaConfig.overlayReads) {
            return original;
        }

        OverlayWorldView overlay = OverlayWorldView.current();
        if (overlay.hasOverlay(pos)) {
            return overlay.getOverlay(pos);
        }

        return original;
    }

    public static boolean interceptSetBlock(Level level, BlockPos pos, BlockState state, int flags) {
        // Always check for IntentMerger first
        IntentMerger merger = INTENT_MERGER.get();
        if (merger == null || !merger.isAccepting()) {
            // No merger or not accepting - let vanilla handle it
            return false;
        }

        // Check thread context
        boolean isMainThread = org.bukkit.Bukkit.isPrimaryThread();
        boolean isPluginPhase = PluginPhaseExecutor.isPluginThread();

        if (!isMainThread && !isPluginPhase) {
            recordAsyncViolation("setBlock");
            if (ChronaConfig.queueAsyncAccess) {
                if (WARNED_ASYNC.compareAndSet(false, true)) {
                    org.apache.logging.log4j.LogManager.getLogger(BukkitCompatLayer.class)
                        .warn("Async setBlock detected - queuing to plugin phase");
                }
                final java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(false);
                PluginPhaseExecutor.runOnPluginThread(() ->
                    result.set(submitIntent(merger, pos, state, true))
                );
                return result.get();
            }

            if (ChronaConfig.throwOnAsyncWrite) {
                throw new IllegalStateException("Async setBlock is not allowed");
            }

            if (ChronaConfig.rejectAsyncWrites) {
                if (ChronaConfig.warnAsyncAccess && WARNED_ASYNC.compareAndSet(false, true)) {
                    org.apache.logging.log4j.LogManager.getLogger(BukkitCompatLayer.class)
                        .warn("Async setBlock rejected - enable queue-async-access to allow safe writes");
                }
                return true;
            }

            // Async access allowed (unsafe)
            SafetyValidator.validateThreadAccess("async write");
            return false;
        }

        return submitIntent(merger, pos, state, isPluginPhase);
    }

    private static boolean submitIntent(IntentMerger merger, BlockPos pos, BlockState state, boolean isPluginPhase) {
        boolean accepted = merger.submit(
            BlockIntent.set(pos, state, isPluginPhase ? "plugin" : "player", System.nanoTime())
        );
        if (!accepted) {
            return false;
        }

        if (isPluginPhase && ChronaConfig.overlayReads) {
            // Plugin phase - use overlay for read-your-writes
            OverlayWorldView.current().setBlock(pos, state);
        }

        // Return true to skip vanilla setBlock (will be applied in commit phase)
        return true;
    }

    private static void recordAsyncViolation(String type) {
        ASYNC_VIOLATIONS.incrementAndGet();
        SafetyValidator.validateThreadAccess("async " + type);
    }
}
