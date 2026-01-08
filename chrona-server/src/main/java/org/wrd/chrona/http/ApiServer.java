package org.wrd.chrona.http;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wrd.chrona.http.api.ApiProvider;
import org.wrd.chrona.http.api.HandlerContext;
import org.wrd.chrona.http.api.HandlerContextImpl;
import org.wrd.chrona.http.api.provider.PlayerListProvider;
import org.wrd.chrona.http.api.provider.PlayerInventoryProvider;
import org.wrd.chrona.http.api.provider.TPSProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;

public class ApiServer implements Runnable, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiServer.class);
    private static final List<ApiProvider> PROVIDERS = List.of(new PlayerListProvider(), new TPSProvider(), new PlayerInventoryProvider());

    private final ServerProperties properties;

    private HttpServer server;

    public ApiServer(ServerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run() {
        LOGGER.info("Starting http server...");

        try {
            server = HttpServer.create(new InetSocketAddress(properties.port()), 0);
        } catch (IOException e) {
            LOGGER.error("Failed to start http server", e);
        }

        PROVIDERS.forEach((provider) -> {
            server.createContext("/api/" + provider.name(), (exchange) -> {
                HandlerContext context = new HandlerContextImpl(exchange);

                switch (context.method()) {
                    case GET -> provider.get(context);
                    case POST -> provider.post(context);
                }
            });
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @Override
    public void close() throws Exception {
        LOGGER.info("Stopping http server...");

        server.stop(0);
    }
}
