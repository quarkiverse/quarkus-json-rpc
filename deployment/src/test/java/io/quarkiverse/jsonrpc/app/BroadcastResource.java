package io.quarkiverse.jsonrpc.app;

import jakarta.inject.Inject;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.quarkiverse.jsonrpc.api.JsonRPCBroadcaster;
import io.smallrye.common.annotation.NonBlocking;

@JsonRPCApi
public class BroadcastResource {

    @Inject
    JsonRPCBroadcaster broadcaster;

    @NonBlocking
    public String triggerBroadcast(String method, String message) {
        broadcaster.broadcast(method, message);
        return "broadcast sent";
    }

    @NonBlocking
    public boolean sendToSession(String sessionId, String method, String message) {
        return broadcaster.send(sessionId, method, message);
    }

    @NonBlocking
    public int getSessionCount() {
        return broadcaster.connectedSessions().size();
    }
}
