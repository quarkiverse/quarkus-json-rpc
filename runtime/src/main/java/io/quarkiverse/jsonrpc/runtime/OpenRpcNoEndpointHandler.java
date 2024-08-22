package io.quarkiverse.jsonrpc.runtime;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class OpenRpcNoEndpointHandler implements Handler<RoutingContext> {
    private static final String CONTENT_TYPE = "text/plain; charset=UTF-8";
    private static final String MESSAGE = "OpenRPC Schema not generated. Make sure you have a OpenRPC Endpoint.";

    @Override
    public void handle(RoutingContext event) {
        HttpServerResponse response = event.response();
        response.headers().set(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE);
        response.setStatusCode(404).end(MESSAGE);
    }
}
