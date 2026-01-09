package org.wrd.chrona.async.entity.path;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.Util;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wrd.chrona.configuration.ChronaConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * used to handle the scheduling of async path processing
 */
public class AsyncPathProcessor {

    private static final String THREAD_PREFIX = "Chrona Async Pathfinding";
    private static final Logger LOGGER = LogManager.getLogger(THREAD_PREFIX);
    private static long lastWarnMillis = System.currentTimeMillis();
    public static ThreadPoolExecutor PATH_PROCESSING_EXECUTOR = null;

    public static void init(ChronaConfiguration config) {
        if (PATH_PROCESSING_EXECUTOR == null) {
            PATH_PROCESSING_EXECUTOR = new ThreadPoolExecutor(
                    getCorePoolSize(config),
                    getMaxPoolSize(config),
                    getKeepAliveTime(config), TimeUnit.SECONDS,
                    getQueueImpl(config),
                    getThreadFactory(config),
                    getRejectedPolicy(config)
            );
        } else {
            // Temp no-op
            //throw new IllegalStateException();
        }
    }

    protected static CompletableFuture<Void> queue(@NotNull AsyncPath path) {
        return CompletableFuture.runAsync(path::process, PATH_PROCESSING_EXECUTOR)
                .orTimeout(60L, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    if (throwable instanceof TimeoutException e) {
                        LOGGER.warn("Async Pathfinding process timed out", e);
                    } else LOGGER.warn("Error occurred while processing async path", throwable);
                    return null;
                });
    }

    /**
     * takes a possibly unprocessed path, and waits until it is completed
     * the consumer will be immediately invoked if the path is already processed
     * the consumer will always be called on the main thread
     *
     * @param path            a path to wait on
     * @param afterProcessing a consumer to be called
     */
    public static void awaitProcessing(@Nullable Path path, Consumer<@Nullable Path> afterProcessing) {
        if (path != null && !path.isProcessed() && path instanceof AsyncPath asyncPath) {
            asyncPath.schedulePostProcessing(() -> afterProcessing.accept(path)); // Reduce double lambda allocation
        } else {
            afterProcessing.accept(path);
        }
    }

    private static int getCorePoolSize(ChronaConfiguration config) {
        return 1;
    }

    private static int getMaxPoolSize(ChronaConfiguration config) {
        return config.optimizationAsyncPathfindingMaxThreads.getValue();
    }

    private static long getKeepAliveTime(ChronaConfiguration config) {
        return config.optimizationAsyncPathfindingKeepalive.getValue();
    }

    private static BlockingQueue<Runnable> getQueueImpl(ChronaConfiguration config) {
        final int queueCapacity = config.optimizationAsyncPathfindingQueueSize.getValue();

        return new LinkedBlockingQueue<>(queueCapacity);
    }

    private static @NotNull ThreadFactory getThreadFactory(ChronaConfiguration config) {
        return new ThreadFactoryBuilder()
                .setNameFormat(THREAD_PREFIX + " Thread - %d")
                .setPriority(Thread.NORM_PRIORITY - 2)
                .setUncaughtExceptionHandler(Util::onThreadException)
                .build();
    }

    private static @NotNull RejectedExecutionHandler getRejectedPolicy(ChronaConfiguration config) {
        return (Runnable rejectedTask, ThreadPoolExecutor executor) -> {
            BlockingQueue<Runnable> workQueue = executor.getQueue();
            if (!executor.isShutdown()) {
                switch (PathfindTaskRejectPolicy.valueOf(config.optimizationAsyncPathfindingRejectPolicy.getValue())) {
                    case FLUSH_ALL -> {
                        if (!workQueue.isEmpty()) {
                            List<Runnable> pendingTasks = new ArrayList<>(workQueue.size());

                            workQueue.drainTo(pendingTasks);

                            for (Runnable pendingTask : pendingTasks) {
                                pendingTask.run();
                            }
                        }
                        rejectedTask.run();
                    }
                    case CALLER_RUNS -> rejectedTask.run();
                }
            }

            if (System.currentTimeMillis() - lastWarnMillis > 30000L) {
                LOGGER.warn("Async pathfinding processor is busy! Pathfinding tasks will be treated as policy defined in config. Increasing max-threads in Leaf config may help.");
                lastWarnMillis = System.currentTimeMillis();
            }
        };
    }
}