package org.wrd.chrona.panel.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.wrd.chrona.panel.WrdHubPanelLinker;

public class VersionProvider implements ApiProvider {
    @Override
    public String id() {
        return "version";
    }

    @Override
    public JsonElement provide() {
        return new JsonPrimitive(WrdHubPanelLinker.API_VERSION);
    }
}
