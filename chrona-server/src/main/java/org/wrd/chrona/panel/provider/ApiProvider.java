package org.wrd.chrona.panel.provider;

import com.google.gson.JsonElement;

public interface ApiProvider {
    String id();

    JsonElement provide();
}
