package org.wrd.chrona.tick.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.wrd.chrona.tick.phase.PluginPhaseExecutor;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncScheduler {
    private static final AtomicInteger TASK_ID_COUNTER = new AtomicInteger(0);

    private final Queue<ScheduledTask> syncTasks = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<ScheduledTask> delayedTasks = new PriorityQueue<>();
    private long currentTick = 0;

    public BukkitTask runTask(Plugin plugin, Runnable task) {
        ScheduledTask scheduled = new ScheduledTask(
            TASK_ID_COUNTER.incrementAndGet(),
            plugin,
            task,
            currentTick,
            0,
            false
        );

        if (PluginPhaseExecutor.isPluginThread()) {
            task.run();
        } else {
            syncTasks.offer(scheduled);
        }

        return scheduled;
    }

    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        ScheduledTask scheduled = new ScheduledTask(
            TASK_ID_COUNTER.incrementAndGet(),
            plugin,
            task,
            currentTick + delay,
            0,
            false
        );

        synchronized (delayedTasks) {
            delayedTasks.offer(scheduled);
        }

        return scheduled;
    }

    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        ScheduledTask scheduled = new ScheduledTask(
            TASK_ID_COUNTER.incrementAndGet(),
            plugin,
            task,
            currentTick + delay,
            period,
            true
        );

        synchronized (delayedTasks) {
            delayedTasks.offer(scheduled);
        }

        return scheduled;
    }

    public void executePendingTasks() {
        currentTick++;

        while (!syncTasks.isEmpty()) {
            ScheduledTask task = syncTasks.poll();
            if (task != null && !task.isCancelled()) {
                task.run();
            }
        }

        synchronized (delayedTasks) {
            while (!delayedTasks.isEmpty() && delayedTasks.peek().nextRun <= currentTick) {
                ScheduledTask task = delayedTasks.poll();
                if (task != null && !task.isCancelled()) {
                    task.run();
                    if (task.repeating) {
                        task.nextRun = currentTick + task.period;
                        delayedTasks.offer(task);
                    }
                }
            }
        }
    }

    private static class ScheduledTask implements BukkitTask, Comparable<ScheduledTask> {
        private final int id;
        private final Plugin plugin;
        private final Runnable task;
        private long nextRun;
        private final long period;
        private final boolean repeating;
        private volatile boolean cancelled = false;

        ScheduledTask(int id, Plugin plugin, Runnable task, long nextRun, long period, boolean repeating) {
            this.id = id;
            this.plugin = plugin;
            this.task = task;
            this.nextRun = nextRun;
            this.period = period;
            this.repeating = repeating;
        }

        @Override
        public int getTaskId() {
            return id;
        }

        @Override
        public Plugin getOwner() {
            return plugin;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public int compareTo(ScheduledTask other) {
            return Long.compare(this.nextRun, other.nextRun);
        }

        public void run() {
            task.run();
        }
    }
}
