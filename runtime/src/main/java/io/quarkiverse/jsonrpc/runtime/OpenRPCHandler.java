package io.quarkiverse.jsonrpc.runtime;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class OpenRPCHandler implements Handler<RoutingContext> {

    private final String content;

    public OpenRPCHandler(String openrpcJson) {
        this.content = openrpcJson;
    }

    @Override
    public void handle(RoutingContext event) {
        event.response()
                .putHeader("Content-Type", "application/json")
                .putHeader("Cache-Control", "public, max-age=86400")
                .end(content);
    }
}
