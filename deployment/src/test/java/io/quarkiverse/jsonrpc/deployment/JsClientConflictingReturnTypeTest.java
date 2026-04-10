package io.quarkiverse.jsonrpc.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.ConflictingReturnTypeResource;
import io.quarkus.test.QuarkusUnitTest;

public class JsClientConflictingReturnTypeTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(ConflictingReturnTypeResource.class);
            })
            .overrideConfigKey("quarkus.json-rpc.client.enabled", "true")
            .assertException(t -> {
                Assertions.assertTrue(t instanceof IllegalArgumentException,
                        "Expected IllegalArgumentException but got: " + t.getClass().getName());
                Assertions.assertTrue(t.getMessage().contains("conflicting return types"),
                        "Expected message about conflicting return types but got: " + t.getMessage());
            });

    @Test
    public void testValidationFails() {
        Assertions.fail("Should not reach here — build should have failed");
    }
}
