package io.quarkiverse.jsonrpc.websocket.runtime;

import java.util.Objects;

import io.quarkiverse.jsonrpc.runtime.JsonRPCConnection;
import io.vertx.core.http.ServerWebSocket;

/**
 * {@link JsonRPCConnection} backed by a Vert.x {@link ServerWebSocket}.
 */
public final class WebSocketConnection implements JsonRPCConnection {

    private final ServerWebSocket socket;

    public WebSocketConnection(ServerWebSocket socket) {
        this.socket = Objects.requireNonNull(socket, "socket");
    }

    @Override
    public void writeTextMessage(String message) {
        socket.writeTextMessage(message);
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public String path() {
        return socket.path();
    }
}
