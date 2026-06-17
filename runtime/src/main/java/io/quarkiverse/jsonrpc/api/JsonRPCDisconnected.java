package io.quarkiverse.jsonrpc.api;

/**
 * CDI event fired when a WebSocket client disconnects from the JSON-RPC endpoint.
 * The session is already closed and removed when this event fires; observers cannot send
 * messages to the disconnecting client.
 * <p>
 * This event is fired on the Vert.x event loop thread; observers must not perform blocking operations.
 */
public record JsonRPCDisconnected(String sessionId) {
}
