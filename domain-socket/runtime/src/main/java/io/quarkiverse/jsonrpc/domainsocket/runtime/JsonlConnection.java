package io.quarkiverse.jsonrpc.domainsocket.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.JsonRPCConnection;
import io.vertx.core.net.NetSocket;

/**
 * {@link JsonRPCConnection} backed by a Vert.x {@link NetSocket} using JSONL framing
 * (one JSON object per line, newline-delimited).
 */
public final class JsonlConnection implements JsonRPCConnection {
    private static final Logger LOG = Logger.getLogger(JsonlConnection.class);

    private final NetSocket socket;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    JsonlConnection(NetSocket socket) {
        this.socket = java.util.Objects.requireNonNull(socket, "socket");
    }

    void markClosed() {
        closed.set(true);
    }

    @Override
    public void writeTextMessage(String message) {
        String line = message.replace("\n", "").replace("\r", "") + "\n";
        socket.write(line).onFailure(err -> {
            LOG.debugf(err, "Failed to write to domain socket connection");
            markClosed();
        });
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public String path() {
        return null;
    }

    public void close() {
        markClosed();
        socket.close();
    }
}
