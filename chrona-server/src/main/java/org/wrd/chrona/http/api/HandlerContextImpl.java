package org.wrd.chrona.http.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.Unmodifiable;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class HandlerContextImpl implements HandlerContext {
    private final HttpExchange exchange;

    private final RequestMethod method;
    private final Map<String, String> queryMap = new HashMap<>();

    public HandlerContextImpl(HttpExchange exchange) {
        this.exchange = exchange;

        this.method = RequestMethod.valueOf(exchange.getRequestMethod());

        parseQuery();
    }

    private void parseQuery() {
        String query = this.exchange.getRequestURI().getQuery();
        if(query == null) return;

        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                queryMap.put(pair[0], pair[1]);
            } else {
                queryMap.put(pair[0], "");
            }
        }
    }

    @Override
    public RequestMethod method() {
        return method;
    }

    @Override
    public @Unmodifiable Map<String, String> query() {
        return Map.copyOf(queryMap);
    }

    @Override
    public void error(String msg) {
        JsonObject result = new JsonObject();
        result.addProperty("error", msg);

        send(200, result.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void result(JsonElement json) {
        JsonObject result = new JsonObject();
        result.add("result", json);

        send(200, result.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void send(int code, byte[] bytes) {
        try {
            exchange.sendResponseHeaders(code, bytes.length);

            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public HttpExchange exchange() {
        return exchange;
    }
}
