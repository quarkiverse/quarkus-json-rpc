package io.quarkiverse.jsonrpc.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks all connected JSON-RPC sessions with unique IDs.
 * Shared by {@link JsonRPCRouter} and {@link io.quarkiverse.jsonrpc.api.JsonRPCBroadcaster}.
 */
public class JsonRPCSessions {

    private final ConcurrentHashMap<String, JsonRPCConnection> idToConnection = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JsonRPCConnection, String> connectionToId = new ConcurrentHashMap<>();

    /**
     * Register a new session.
     *
     * @return the generated session ID
     */
    public String addSession(JsonRPCConnection connection) {
        String sessionId = UUID.randomUUID().toString();
        idToConnection.put(sessionId, connection);
        connectionToId.put(connection, sessionId);
        return sessionId;
    }

    /**
     * Remove a session.
     */
    public void removeSession(JsonRPCConnection connection) {
        String sessionId = connectionToId.remove(connection);
        if (sessionId != null) {
            idToConnection.remove(sessionId);
        }
    }

    /**
     * Get the connection for a given session ID.
     *
     * @return the connection, or {@code null} if no such session exists
     */
    public JsonRPCConnection getConnection(String sessionId) {
        return idToConnection.get(sessionId);
    }

    /**
     * Get the session ID for a given connection.
     *
     * @return the session ID, or {@code null} if the connection is not tracked
     */
    public String getSessionId(JsonRPCConnection connection) {
        return connectionToId.get(connection);
    }

    /**
     * @return all currently connected connections
     */
    public Collection<JsonRPCConnection> getAllConnections() {
        return List.copyOf(idToConnection.values());
    }

    /**
     * @return the number of currently active connections
     */
    public int getActiveConnectionCount() {
        return idToConnection.size();
    }

    /**
     * @return the set of all connected session IDs
     */
    public Set<String> getSessionIds() {
        return Set.copyOf(idToConnection.keySet());
    }
}
