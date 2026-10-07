package io.quarkiverse.jsonrpc.deployment;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.health.SmallRyeHealthReporter;

public class HealthCheckDisabledJsonRpcTest {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(HelloResource.class))
            .overrideConfigKey("quarkus.json-rpc.health.enabled", "false");

    @Inject
    SmallRyeHealthReporter reporter;

    @Test
    public void testHealthCheckAbsent() {
        String payload = reporter.getHealth().getPayload().toString();
        assertFalse(payload.contains("JSON-RPC"),
                "Health check should not be present when disabled");
    }
}
