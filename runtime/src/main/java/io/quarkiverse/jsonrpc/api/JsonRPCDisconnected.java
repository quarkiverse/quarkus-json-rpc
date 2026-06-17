package io.quarkiverse.jsonrpc.api;

/**
 * CDI event fired when a WebSocket client disconnects from the JSON-RPC endpoint.
 */
public record JsonRPCDisconnected(String sessionId) {
}
