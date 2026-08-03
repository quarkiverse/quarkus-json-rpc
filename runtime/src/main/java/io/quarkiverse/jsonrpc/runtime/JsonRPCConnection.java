package io.quarkiverse.jsonrpc.runtime;

/**
 * Transport-agnostic abstraction for a JSON-RPC connection.
 * Implemented by WebSocketConnection for WebSocket transport
 * and JsonlConnection for JSONL-over-domain-socket transport.
 */
public interface JsonRPCConnection {

    /**
     * Send a text message to the connected client.
     */
    void writeTextMessage(String message);

    /**
     * @return {@code true} if the connection has been closed
     */
    boolean isClosed();

    /**
     * The path this connection was established on (e.g. the WebSocket request URI path).
     * Returns {@code null} for transports without path-based routing (e.g. domain sockets),
     * which grants access to all registered methods regardless of path.
     */
    String path();
}
