package org.wrd.chrona.panel.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class EntitiesProvider implements ApiProvider {
    @Override
    public String id() {
        return "entities";
    }

    @Override
    public JsonElement provide() {
        JsonObject json = new JsonObject();
        json.addProperty("total", Bukkit.getWorlds().stream().mapToInt(World::getEntityCount).sum());
        return json;
    }
}
