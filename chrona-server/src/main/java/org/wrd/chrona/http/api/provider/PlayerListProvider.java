package org.wrd.chrona.http.api.provider;

import com.google.gson.JsonArray;
import org.bukkit.Bukkit;
import org.wrd.chrona.http.api.ApiProvider;
import org.wrd.chrona.http.api.HandlerContext;

public class PlayerListProvider implements ApiProvider {
    @Override
    public String name() {
        return "player_list";
    }

    @Override
    public void get(HandlerContext context) {
        JsonArray json = new JsonArray();
        Bukkit.getOnlinePlayers().forEach((player) -> json.add(player.getUniqueId().toString()));

        context.result(json);
    }
}
