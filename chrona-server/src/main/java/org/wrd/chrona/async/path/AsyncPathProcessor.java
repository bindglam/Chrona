package org.wrd.chrona.async.path;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.util.Util;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class AsyncPathProcessor {
    private static final Logger LOGGER = LogManager.getLogger("Chrona-AsyncPathfinding");
    private static final int CORE_POOL_SIZE = 1;
    private static ThreadPoolExecutor PATH_EXECUTOR;

    public static void init(int maxThreads, int queueSize, long keepAliveSeconds) {
        if (PATH_EXECUTOR != null) return;

        if (maxThreads <= 0) {
            maxThreads = Math.max(Runtime.getRuntime().availableProcessors() / 4, 1);
        }

        if (queueSize <= 0) {
            queueSize = maxThreads * 256;
        }

        PATH_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            maxThreads,
            keepAliveSeconds, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueSize),
            new ThreadFactoryBuilder()
                .setNameFormat("Chrona-AsyncPathfinding-%d")
                .setPriority(Thread.NORM_PRIORITY - 2)
                .setUncaughtExceptionHandler(Util::onThreadException)
                .setDaemon(true)
                .build(),
            new CallerRunsOrFlushPolicy(maxThreads)
        );

        LOGGER.info("Initialized async pathfinding: {} threads, queue size {}", maxThreads, queueSize);
    }

    static CompletableFuture<Void> queue(@NotNull AsyncPath path) {
        if (PATH_EXECUTOR == null) {
            init(-1, -1, 60);
        }

        return CompletableFuture.runAsync(path::process, PATH_EXECUTOR)
            .orTimeout(60L, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                if (throwable instanceof TimeoutException) {
                    LOGGER.warn("Pathfinding timed out");
                } else {
                    LOGGER.error("Pathfinding error", throwable);
                }
                return null;
            });
    }

    public static void awaitProcessing(@Nullable Path path, Consumer<@Nullable Path> afterProcessing) {
        if (path != null && !path.isDone() && path instanceof AsyncPath asyncPath) {
            asyncPath.schedulePostProcessing(() -> {
                try {
                    Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugins()[0],
                        () -> afterProcessing.accept(path)
                    );
                } catch (Exception e) {
                    afterProcessing.accept(path);
                }
            });
        } else {
            afterProcessing.accept(path);
        }
    }

    public static void shutdown() {
        if (PATH_EXECUTOR != null) {
            PATH_EXECUTOR.shutdown();
            try {
                if (!PATH_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    PATH_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException e) {
                PATH_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class CallerRunsOrFlushPolicy implements RejectedExecutionHandler {
        private final boolean useFlush;
        private long lastWarn = 0;

        CallerRunsOrFlushPolicy(int maxThreads) {
            this.useFlush = maxThreads >= 4;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) return;

            if (useFlush) {
                executor.getQueue().clear();
            }

            r.run();

            long now = System.currentTimeMillis();
            if (now - lastWarn > 30000L) {
                LOGGER.warn("Pathfinding queue saturated - increase max-threads if this persists");
                lastWarn = now;
            }
        }
    }
}
