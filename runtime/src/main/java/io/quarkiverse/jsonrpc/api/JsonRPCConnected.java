package io.quarkiverse.jsonrpc.api;

/**
 * CDI event fired when a new WebSocket client connects to the JSON-RPC endpoint.
 */
public record JsonRPCConnected(String sessionId) {
}
