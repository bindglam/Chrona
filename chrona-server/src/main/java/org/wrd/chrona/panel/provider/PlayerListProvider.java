package org.wrd.chrona.panel.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;

public class PlayerListProvider implements ApiProvider {
    @Override
    public String id() {
        return "players";
    }

    @Override
    public JsonElement provide() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        JsonObject json = new JsonObject();
        json.addProperty("online", players.size());
        json.addProperty("max", Bukkit.getMaxPlayers());

        JsonArray listJson = new JsonArray();
        players.forEach((player) -> listJson.add(parsePlayer(player)));
        json.add("list", listJson);

        return json;
    }

    private JsonObject parsePlayer(Player player) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", player.getUniqueId().toString());
        json.addProperty("name", player.getName());
        json.addProperty("world", player.getWorld().getName());
        json.addProperty("gamemmode", player.getGameMode().toString());
        json.addProperty("health", player.getHealth());
        json.addProperty("maxHealth", Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
        json.addProperty("food", player.getFoodLevel());
        json.addProperty("level", player.getLevel());
        json.addProperty("xp", player.getTotalExperience());
        return json;
    }
}
