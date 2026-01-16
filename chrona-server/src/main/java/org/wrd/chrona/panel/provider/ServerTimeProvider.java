package org.wrd.chrona.panel.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class ServerTimeProvider implements ApiProvider {
    @Override
    public String id() {
        return "serverTime";
    }

    @Override
    public JsonElement provide() {
        return new JsonPrimitive(System.currentTimeMillis());
    }
}
