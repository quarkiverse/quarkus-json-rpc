package io.quarkiverse.jsonrpc.domainsocket.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkiverse.jsonrpc.runtime.JsonRPCConnection;
import io.vertx.core.net.NetSocket;

/**
 * {@link JsonRPCConnection} backed by a Vert.x {@link NetSocket} using JSONL framing
 * (one JSON object per line, newline-delimited).
 */
public final class JsonlConnection implements JsonRPCConnection {

    private final NetSocket socket;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public JsonlConnection(NetSocket socket) {
        this.socket = socket;
        socket.closeHandler(v -> closed.set(true));
    }

    @Override
    public void writeTextMessage(String message) {
        // Strip indentation newlines (from Jackson INDENT_OUTPUT) to produce
        // a single-line JSON message, then append \n for JSONL framing.
        // This is safe because literal newlines inside JSON string values are
        // escaped by Jackson as \\n (two characters), not as actual newline bytes.
        socket.write(message.replace("\n", "").replace("\r", "") + "\n");
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
        socket.close();
    }
}
