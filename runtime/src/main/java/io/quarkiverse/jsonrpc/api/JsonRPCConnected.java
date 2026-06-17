package io.quarkiverse.jsonrpc.api;

/**
 * CDI event fired when a new WebSocket client connects to the JSON-RPC endpoint.
 * The event fires after session registration and identity setup but before message handlers
 * are attached, so the socket cannot yet receive messages during observer execution.
 * <p>
 * This event is fired on the Vert.x event loop thread; observers must not perform blocking operations.
 */
public record JsonRPCConnected(String sessionId) {
}
