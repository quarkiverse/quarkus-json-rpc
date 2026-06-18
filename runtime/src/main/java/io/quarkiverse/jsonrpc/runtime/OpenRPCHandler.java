package io.quarkiverse.jsonrpc.runtime;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

public class OpenRPCHandler implements Handler<RoutingContext> {

    private final Buffer content;

    public OpenRPCHandler(String openrpcJson) {
        this.content = Buffer.buffer(openrpcJson);
    }

    @Override
    public void handle(RoutingContext event) {
        event.response()
                .putHeader("Content-Type", "application/json")
                .end(content);
    }
}
