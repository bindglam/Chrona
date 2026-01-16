package org.wrd.chrona.panel.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class WorldsProvider implements ApiProvider {
    @Override
    public String id() {
        return "worlds";
    }

    @Override
    public JsonElement provide() {
        JsonArray json = new JsonArray();

        Bukkit.getWorlds().forEach((world) -> json.add(parseWorld(world)));

        return json;
    }

    private JsonObject parseWorld(World world) {
        JsonObject json = new JsonObject();
        json.addProperty("name", world.getName());
        json.addProperty("players", world.getPlayerCount());
        json.addProperty("loadedChunks", world.getLoadedChunks().length);
        return json;
    }
}
