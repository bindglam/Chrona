package org.wrd.chrona.http.api.provider;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.wrd.chrona.http.api.ApiProvider;
import org.wrd.chrona.http.api.HandlerContext;

import java.nio.charset.StandardCharsets;

public class TPSProvider implements ApiProvider {
    @Override
    public String name() {
        return "tps";
    }

    @Override
    public void get(HandlerContext context) {
        JsonObject obj = new JsonObject();
        double[] tps = Bukkit.getTPS();
        obj.addProperty("1", tps[0]);
        obj.addProperty("5", tps[1]);
        obj.addProperty("15", tps[2]);

        context.result(obj);
    }
}
