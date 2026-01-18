package org.wrd.chrona.util.lockfree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class LockFreeExecutor implements ExecutorService {
    private static final Logger LOGGER = LogManager.getLogger(LockFreeExecutor.class);

    private final MpmcQueue<Runnable> queue;
    private final Worker[] workers;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicLong tasksSubmitted = new AtomicLong(0);
    private final AtomicLong tasksCompleted = new AtomicLong(0);
    private final AtomicLong tasksRejected = new AtomicLong(0);
    private final AtomicLong lastQueueFullLogMs = new AtomicLong(0);
    private final String poolName;
    private static final long QUEUE_FULL_LOG_INTERVAL_MS = 1000;

    public LockFreeExecutor(int threads, int queueCapacity) {
        this(threads, queueCapacity, "LockFree");
    }

    public LockFreeExecutor(int threads, int queueCapacity, String poolName) {
        this.queue = new MpmcQueue<>(queueCapacity);
        this.workers = new Worker[threads];
        this.poolName = poolName;

        for (int i = 0; i < threads; i++) {
            workers[i] = new Worker(queue, shutdown, tasksCompleted);
            Thread thread = new Thread(workers[i], poolName + "-Worker-" + i);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.start();
        }

        LOGGER.info("Initialized {} with {} workers, queue capacity: {}", poolName, threads, queueCapacity);
    }

    @Override
    public void execute(@NotNull Runnable command) {
        if (shutdown.get()) {
            throw new RejectedExecutionException(poolName + " is shutdown");
        }

        tasksSubmitted.incrementAndGet();
        if (!queue.offer(command)) {
            tasksRejected.incrementAndGet();
            logQueueFull();
            command.run();
        } else {
            // Unpark one worker (not all) for better efficiency
            LockSupport.unpark(workers[(int) (tasksSubmitted.get() % workers.length)].thread);
        }
    }

    public <T> FutureTask<T> submitOrRun(Callable<T> task) {
        if (shutdown.get()) {
            throw new RejectedExecutionException(poolName + " is shutdown");
        }

        tasksSubmitted.incrementAndGet();
        FutureTask<T> future = new FutureTask<>(task);
        if (!queue.offer(future)) {
            tasksRejected.incrementAndGet();
            logQueueFull();
            future.run();
        } else {
            // Unpark one worker
            LockSupport.unpark(workers[(int) (tasksSubmitted.get() % workers.length)].thread);
        }
        return future;
    }

    private void logQueueFull() {
        long nowMs = System.currentTimeMillis();
        long lastMs = lastQueueFullLogMs.get();
        if (nowMs - lastMs < QUEUE_FULL_LOG_INTERVAL_MS) {
            return;
        }
        if (lastQueueFullLogMs.compareAndSet(lastMs, nowMs)) {
            LOGGER.warn("{} queue full, executing task on caller thread", poolName);
        }
    }

    @Override
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            LOGGER.info("Shutting down {} - Tasks: submitted={}, completed={}, rejected={}",
                poolName, tasksSubmitted.get(), tasksCompleted.get(), tasksRejected.get());
            for (Worker worker : workers) {
                LockSupport.unpark(worker.thread);
            }
        }
    }

    /**
     * Get executor statistics
     */
    public ExecutorStats getStats() {
        return new ExecutorStats(
            tasksSubmitted.get(),
            tasksCompleted.get(),
            tasksRejected.get(),
            queue.size(),
            workers.length
        );
    }

    public record ExecutorStats(
        long submitted,
        long completed,
        long rejected,
        int queueSize,
        int workerCount
    ) {
        public long pending() {
            return submitted - completed;
        }

        public double rejectionRate() {
            return submitted > 0 ? (double) rejected / submitted : 0.0;
        }
    }

    @NotNull
    @Override
    public java.util.List<Runnable> shutdownNow() {
        shutdown();
        return java.util.Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public boolean isTerminated() {
        return shutdown.get();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) {
        return true;
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Callable<T> task) {
        return submitOrRun(task);
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Runnable task, T result) {
        return submit(() -> {
            task.run();
            return result;
        });
    }

    @NotNull
    @Override
    public Future<?> submit(@NotNull Runnable task) {
        return submit(task, null);
    }

    @NotNull
    @Override
    public <T> java.util.List<Future<T>> invokeAll(@NotNull java.util.Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public <T> java.util.List<Future<T>> invokeAll(@NotNull java.util.Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public <T> T invokeAny(@NotNull java.util.Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(@NotNull java.util.Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    private static class Worker implements Runnable {
        private final MpmcQueue<Runnable> queue;
        private final AtomicBoolean shutdown;
        private final AtomicLong tasksCompleted;
        volatile Thread thread;

        Worker(MpmcQueue<Runnable> queue, AtomicBoolean shutdown, AtomicLong tasksCompleted) {
            this.queue = queue;
            this.shutdown = shutdown;
            this.tasksCompleted = tasksCompleted;
        }

        @Override
        public void run() {
            thread = Thread.currentThread();
            String threadName = thread.getName();

            LOGGER.debug("Worker {} started", threadName);

            while (!shutdown.get()) {
                Runnable task = queue.poll();
                if (task != null) {
                    try {
                        task.run();
                        tasksCompleted.incrementAndGet();
                    } catch (Throwable t) {
                        LOGGER.error("Task execution failed in worker {}", threadName, t);
                        // Continue processing other tasks even if one fails
                    }
                } else {
                    // Park until woken up by new task
                    LockSupport.park();
                }
            }

            LOGGER.debug("Worker {} stopped", threadName);
        }
    }
}
