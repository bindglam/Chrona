package org.wrd.chrona.panel.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

public class TPSProvider implements ApiProvider {
    @Override
    public String id() {
        return "tps";
    }

    @Override
    public JsonElement provide() {
        double[] tps = Bukkit.getTPS();

        JsonObject json = new JsonObject();
        json.addProperty("1", tps[0]);
        json.addProperty("5", tps[1]);
        json.addProperty("15", tps[2]);
        return json;
    }
}
