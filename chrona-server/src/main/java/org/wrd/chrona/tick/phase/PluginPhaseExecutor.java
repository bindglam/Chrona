package org.wrd.chrona.tick.phase;

import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.wrd.chrona.tick.overlay.OverlayWorldView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class PluginPhaseExecutor {
    private static final AtomicBoolean IS_PLUGIN_THREAD = new AtomicBoolean(false);

    private final MinecraftServer server;
    private final Thread pluginThread;

    public PluginPhaseExecutor(MinecraftServer server) {
        this.server = server;
        this.pluginThread = Thread.currentThread();
    }

    public void execute(Runnable pluginTasks) {
        IS_PLUGIN_THREAD.set(true);
        OverlayWorldView view = new OverlayWorldView();
        OverlayWorldView.setCurrent(view);

        try {
            pluginTasks.run();
        } finally {
            OverlayWorldView.clear();
            IS_PLUGIN_THREAD.set(false);
        }
    }

    public static boolean isPluginThread() {
        return IS_PLUGIN_THREAD.get();
    }

    public static void ensureMainThread(String reason) {
        if (!isPluginThread()) {
            throw new IllegalStateException("Asynchronous " + reason + "!");
        }
    }

    public static void runOnPluginThread(Runnable task) {
        if (isPluginThread()) {
            task.run();
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            org.bukkit.plugin.Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
            if (plugins == null || plugins.length == 0) {
                throw new IllegalStateException("No plugins loaded - cannot schedule task");
            }
            Bukkit.getScheduler().runTask(
                plugins[0],
                () -> {
                    try {
                        task.run();
                    } finally {
                        latch.countDown();
                    }
                }
            );

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
