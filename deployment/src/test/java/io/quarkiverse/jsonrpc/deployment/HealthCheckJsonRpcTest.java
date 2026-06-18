package io.quarkiverse.jsonrpc.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.health.SmallRyeHealth;
import io.smallrye.health.SmallRyeHealthReporter;

public class HealthCheckJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(HelloResource.class));

    @Inject
    SmallRyeHealthReporter reporter;

    @Test
    public void testHealthCheckPresent() {
        SmallRyeHealth health = reporter.getHealth();
        assertEquals(HealthCheckResponse.Status.UP, health.getStatus());

        String payload = health.getPayload().toString();
        assertTrue(payload.contains("JSON-RPC WebSocket"), "Health check should be named 'JSON-RPC WebSocket'");
        assertTrue(payload.contains("activeConnections"), "Health check should report activeConnections");
    }
}
