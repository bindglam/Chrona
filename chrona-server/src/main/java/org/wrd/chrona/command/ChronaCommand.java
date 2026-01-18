package org.wrd.chrona.command;

import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.wrd.chrona.diagnostics.TickDiagnostics;
import org.wrd.chrona.tick.AsyncTickExecutor;
import org.wrd.chrona.tick.WorldTickManager;

import java.util.Map;

public class ChronaCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== Chrona Async Tick System ===");
            sender.sendMessage(ChatColor.YELLOW + "/chrona stats - View tick statistics");
            sender.sendMessage(ChatColor.YELLOW + "/chrona info - System information");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "stats" -> {
                Map<String, TickDiagnostics.TickMetrics> metrics = TickDiagnostics.getMetrics();
                sender.sendMessage(ChatColor.GOLD + "=== Tick Phase Statistics (ms) ===");

                for (Map.Entry<String, TickDiagnostics.TickMetrics> entry : metrics.entrySet()) {
                    TickDiagnostics.TickMetrics m = entry.getValue();
                    sender.sendMessage(ChatColor.YELLOW + "World: " + entry.getKey());
                    sender.sendMessage("  Snapshot: " + String.format("%.2f", m.snapshotTime.getAverage()));
                    sender.sendMessage("  Compute:  " + String.format("%.2f", m.computeTime.getAverage()));
                    sender.sendMessage("  Merge:    " + String.format("%.2f", m.mergeTime.getAverage()));
                    sender.sendMessage("  Commit:   " + String.format("%.2f", m.commitTime.getAverage()));
                    sender.sendMessage("  Plugin:   " + String.format("%.2f", m.pluginTime.getAverage()));
                    sender.sendMessage("  Total:    " + String.format("%.2f", m.getTotalAverage()));
                    sender.sendMessage("  Ticks:    " + m.totalTicks);
                }
                return true;
            }
            case "info" -> {
                sender.sendMessage(ChatColor.GOLD + "=== Chrona System Info ===");
                sender.sendMessage("Parallel Threads: " + Runtime.getRuntime().availableProcessors());
                sender.sendMessage("Worlds: " + Bukkit.getWorlds().size());

                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    ServerLevel level = ((CraftWorld) world).getHandle();
                    AsyncTickExecutor executor = WorldTickManager.getInstance().getOrCreateExecutor(level);
                    sender.sendMessage("  " + world.getName() + ": Active");
                }
                return true;
            }
        }

        return false;
    }
}
