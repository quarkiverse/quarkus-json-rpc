package io.quarkiverse.jsonrpc.runtime;

import org.jboss.logging.Logger;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.RoutingContext;

/**
 * This is the main entry point for Json RPC communication
 */
public class JsonRPCWebSocket implements Handler<RoutingContext> {
    private static final Logger LOG = Logger.getLogger(JsonRPCWebSocket.class.getName());

    private final JsonRPCRouter jsonRpcRouter;

    public JsonRPCWebSocket(JsonRPCRouter jsonRpcRouter) {
        this.jsonRpcRouter = jsonRpcRouter;
    }

    @Override
    public void handle(RoutingContext event) {
        if (WEBSOCKET.equalsIgnoreCase(event.request().getHeader(UPGRADE)) && !event.request().isEnded()) {
            // Capture security identity before WebSocket upgrade so it can be
            // associated with the socket and used for method-level authorization.
            SecurityIdentity identity = null;
            if (event.user() instanceof QuarkusHttpUser httpUser) {
                identity = httpUser.getSecurityIdentity();
            }

            final SecurityIdentity capturedIdentity = identity;
            event.request().toWebSocket(new Handler<AsyncResult<ServerWebSocket>>() {
                @Override
                public void handle(AsyncResult<ServerWebSocket> event) {
                    if (event.succeeded()) {
                        ServerWebSocket socket = event.result();
                        jsonRpcRouter.addSocket(socket, capturedIdentity);
                    } else {
                        LOG.error("Failed to connect to json-rpc websocket server", event.cause());
                    }
                }
            });
            return;
        }
        event.next();
    }

    private static final String UPGRADE = "Upgrade";
    private static final String WEBSOCKET = "websocket";
}
