package io.quarkiverse.jsonrpc.app;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkiverse.jsonrpc.api.JsonRPCConnected;
import io.quarkiverse.jsonrpc.api.JsonRPCDisconnected;

@ApplicationScoped
public class ConnectionObserver {

    private static final List<String> connectedSessions = new CopyOnWriteArrayList<>();
    private static final List<String> disconnectedSessions = new CopyOnWriteArrayList<>();
    private static volatile CountDownLatch disconnectLatch = new CountDownLatch(1);

    void onConnect(@Observes JsonRPCConnected event) {
        connectedSessions.add(event.sessionId());
    }

    void onDisconnect(@Observes JsonRPCDisconnected event) {
        disconnectedSessions.add(event.sessionId());
        disconnectLatch.countDown();
    }

    public static List<String> getConnectedSessions() {
        return connectedSessions;
    }

    public static List<String> getDisconnectedSessions() {
        return disconnectedSessions;
    }

    public static CountDownLatch getDisconnectLatch() {
        return disconnectLatch;
    }

    public static void reset() {
        connectedSessions.clear();
        disconnectedSessions.clear();
        disconnectLatch = new CountDownLatch(1);
    }
}
