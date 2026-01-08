package org.wrd.chrona.http.api;

import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

public interface HandlerContext {
    RequestMethod method();

    @Unmodifiable Map<String, String> query();

    void error(String msg);

    void result(JsonElement json);

    void send(int code, byte[] bytes);

    HttpExchange exchange();
}
