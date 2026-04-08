package io.quarkiverse.jsonrpc.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.http.ServerWebSocket;

/**
 * Tracks all connected WebSocket sessions with unique IDs.
 * Shared by {@link JsonRPCRouter} and {@link io.quarkiverse.jsonrpc.api.JsonRPCBroadcaster}.
 */
public class JsonRPCSessions {

    private final ConcurrentHashMap<String, ServerWebSocket> idToSocket = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ServerWebSocket, String> socketToId = new ConcurrentHashMap<>();

    /**
     * Register a new WebSocket session.
     *
     * @return the generated session ID
     */
    public String addSession(ServerWebSocket socket) {
        String sessionId = UUID.randomUUID().toString();
        idToSocket.put(sessionId, socket);
        socketToId.put(socket, sessionId);
        return sessionId;
    }

    /**
     * Remove a WebSocket session.
     */
    public void removeSession(ServerWebSocket socket) {
        String sessionId = socketToId.remove(socket);
        if (sessionId != null) {
            idToSocket.remove(sessionId);
        }
    }

    /**
     * Get the socket for a given session ID.
     *
     * @return the socket, or {@code null} if no such session exists
     */
    public ServerWebSocket getSocket(String sessionId) {
        return idToSocket.get(sessionId);
    }

    /**
     * Get the session ID for a given socket.
     *
     * @return the session ID, or {@code null} if the socket is not tracked
     */
    public String getSessionId(ServerWebSocket socket) {
        return socketToId.get(socket);
    }

    /**
     * @return all currently connected sockets
     */
    public Collection<ServerWebSocket> getAllSockets() {
        return List.copyOf(idToSocket.values());
    }

    /**
     * @return the set of all connected session IDs
     */
    public Set<String> getSessionIds() {
        return Set.copyOf(idToSocket.keySet());
    }
}
