package org.wrd.chrona.panel.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InventoriesProvider implements ApiProvider {
    @Override
    public String id() {
        return "inventories";
    }

    @Override
    public JsonElement provide() {
        JsonObject json = new JsonObject();

        Bukkit.getOnlinePlayers().forEach((player) -> json.add(player.getUniqueId().toString(), parseInventory(player)));

        return json;
    }

    private JsonObject parseInventory(Player player) {
        JsonObject json = new JsonObject();

        JsonArray slotsJson = new JsonArray();
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if(itemStack == null) {
                slotsJson.add(new JsonObject());
                continue;
            }
            slotsJson.add(Bukkit.getUnsafe().serializeItemAsJson(itemStack));
        }
        json.add("slots", slotsJson);

        return json;
    }
}
