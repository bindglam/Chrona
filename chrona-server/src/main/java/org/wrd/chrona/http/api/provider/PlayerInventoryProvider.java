package org.wrd.chrona.http.api.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.wrd.chrona.http.api.ApiProvider;
import org.wrd.chrona.http.api.HandlerContext;

import java.util.UUID;

public class PlayerInventoryProvider implements ApiProvider {
    @Override
    public String name() {
        return "player_inventory";
    }

    @Override
    public void get(HandlerContext context) {
        UUID uuid = UUID.fromString(context.query().get("uuid"));
        Player player = Bukkit.getPlayer(uuid);
        if(player == null) {
            context.error("That player is not found");
            return;
        }
        Inventory inventory = player.getInventory();

        JsonObject obj = new JsonObject();
        JsonArray contents = new JsonArray();

        for (ItemStack itemStack : inventory.getContents()) {
            if(itemStack == null) {
                contents.add(new JsonObject());
                continue;
            }

            contents.add(Bukkit.getUnsafe().serializeItemAsJson(itemStack));
        }

        obj.add("contents", contents);

        context.result(obj);
    }
}
