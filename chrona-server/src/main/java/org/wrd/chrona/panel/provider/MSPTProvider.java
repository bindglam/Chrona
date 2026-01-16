package org.wrd.chrona.panel.provider;

import ca.spottedleaf.moonrise.common.time.TickData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;

public class MSPTProvider implements ApiProvider {
    @Override
    public String id() {
        return "mspt";
    }

    @Override
    public JsonElement provide() {
        TickData.MSPTData mspt = MinecraftServer.getServer().getMSPTData5s();
        JsonObject json = new JsonObject();
        if(mspt != null) {
            json.addProperty("mean", mspt.avg());
            json.addProperty("max", Arrays.stream(mspt.rawData()).max().orElse(0L));
        } else {
            json.addProperty("mean", 0.0);
            json.addProperty("max", 0.0);
        }
        return json;
    }
}
