package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.ConnectionObserver;
import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;

public class ConnectionLifecycleTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, ConnectionObserver.class);
            });

    @BeforeEach
    void resetObserver() {
        ConnectionObserver.reset();
    }

    @Test
    public void testConnectEventFired() throws Exception {
        getJsonRpcResponse("HelloResource#hello", Map.of("name", "Test"));

        Assertions.assertFalse(ConnectionObserver.getConnectedSessions().isEmpty(),
                "Expected at least one connect event");
        String sessionId = ConnectionObserver.getConnectedSessions().get(0);
        Assertions.assertNotNull(sessionId);
        Assertions.assertFalse(sessionId.isEmpty());
    }

    @Test
    public void testDisconnectEventFired() throws Exception {
        getJsonRpcResponse("HelloResource#hello", Map.of("name", "Test"));

        Assertions.assertTrue(ConnectionObserver.getDisconnectLatch().await(5, TimeUnit.SECONDS),
                "Expected disconnect event within 5 seconds");

        String sessionId = ConnectionObserver.getDisconnectedSessions().get(0);
        Assertions.assertNotNull(sessionId);
        Assertions.assertFalse(sessionId.isEmpty());
    }

    @Test
    public void testConnectAndDisconnectSameSessionId() throws Exception {
        getJsonRpcResponse("HelloResource#hello", Map.of("name", "Test"));

        Assertions.assertTrue(ConnectionObserver.getDisconnectLatch().await(5, TimeUnit.SECONDS),
                "Expected disconnect event within 5 seconds");

        Assertions.assertEquals(1, ConnectionObserver.getConnectedSessions().size());
        Assertions.assertEquals(1, ConnectionObserver.getDisconnectedSessions().size());
        Assertions.assertEquals(
                ConnectionObserver.getConnectedSessions().get(0),
                ConnectionObserver.getDisconnectedSessions().get(0));
    }
}
