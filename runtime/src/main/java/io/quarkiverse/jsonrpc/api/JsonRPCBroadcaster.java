package io.quarkiverse.jsonrpc.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.JsonRPCSessions;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCCodec;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCNotification;
import io.vertx.core.http.ServerWebSocket;

/**
 * Injectable service for pushing JSON-RPC 2.0 notifications to connected WebSocket clients.
 *
 * <p>
 * Inject this into your {@link JsonRPCApi @JsonRPCApi} beans (or any CDI bean) to send
 * server-initiated messages:
 *
 * <pre>
 * &#64;JsonRPCApi
 * public class MyService {
 *     &#64;Inject
 *     JsonRPCBroadcaster broadcaster;
 *
 *     public String doWork() {
 *         broadcaster.broadcast("progress", Map.of("percent", 50));
 *         return "done";
 *     }
 * }
 * </pre>
 */
public class JsonRPCBroadcaster {
    private static final Logger LOG = Logger.getLogger(JsonRPCBroadcaster.class);

    private final JsonRPCCodec codec;
    private final JsonRPCSessions sessions;

    public JsonRPCBroadcaster(JsonRPCCodec codec, JsonRPCSessions sessions) {
        this.codec = codec;
        this.sessions = sessions;
    }

    /**
     * Send a JSON-RPC notification to ALL connected clients.
     *
     * @param method the notification method name
     * @param data the notification payload (will be placed under the {@code "result"} key in params)
     */
    public void broadcast(String method, Object data) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("result", data);
        JsonRPCNotification notification = new JsonRPCNotification(method, params);
        for (ServerWebSocket socket : sessions.getAllSockets()) {
            codec.writeNotification(socket, notification);
        }
    }

    /**
     * Send a JSON-RPC notification to a specific client session.
     *
     * @param sessionId the target session ID
     * @param method the notification method name
     * @param data the notification payload (will be placed under the {@code "result"} key in params)
     * @return {@code true} if the session was found and the message was sent, {@code false} otherwise
     */
    public boolean send(String sessionId, String method, Object data) {
        ServerWebSocket socket = sessions.getSocket(sessionId);
        if (socket == null) {
            LOG.debugf("Cannot send to session %s: not found", sessionId);
            return false;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("result", data);
        codec.writeNotification(socket, new JsonRPCNotification(method, params));
        return true;
    }

    /**
     * @return the set of all currently connected session IDs
     */
    public Set<String> connectedSessions() {
        return sessions.getSessionIds();
    }
}
