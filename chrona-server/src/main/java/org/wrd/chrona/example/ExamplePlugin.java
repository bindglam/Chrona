package org.wrd.chrona.example;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ExamplePlugin enabled - fully compatible with Chrona async tick system");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Block block = event.getPlayer().getLocation().getBlock();

        // This works transparently with Chrona's async system
        // 1. getBlock() reads from overlay if plugin wrote to it this tick
        // 2. setType() creates BlockIntent internally
        // 3. Change visible immediately to plugin, applied in Commit Phase

        if (block.getType() == Material.AIR) {
            block.setType(Material.GLASS);
        }

        // Scheduler also works seamlessly
        Bukkit.getScheduler().runTaskLater(this, () -> {
            block.setType(Material.AIR);
        }, 20L);
    }
}
