package org.wrd.chrona.http.api.provider;

import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.wrd.chrona.http.api.ApiProvider;
import org.wrd.chrona.http.api.HandlerContext;

public class PlayerCountProvider implements ApiProvider {
    @Override
    public String name() {
        return "player_cnt";
    }

    @Override
    public void get(HandlerContext context) {
        context.result(new JsonPrimitive(Bukkit.getOnlinePlayers().size()));
    }
}
