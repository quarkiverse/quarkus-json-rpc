package io.quarkiverse.jsonrpc.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.RunOnVirtualThreadMultiResource;
import io.quarkus.test.QuarkusUnitTest;

public class RunOnVirtualThreadMultiValidationTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(RunOnVirtualThreadMultiResource.class);
            })
            .assertException(t -> {
                Assertions.assertTrue(t instanceof IllegalArgumentException,
                        "Expected IllegalArgumentException but got: " + t.getClass().getName());
                Assertions.assertTrue(
                        t.getMessage().contains("cannot use @RunOnVirtualThread with a streaming return type"),
                        "Expected message about streaming return type but got: " + t.getMessage());
            });

    @Test
    public void testValidationFails() {
        Assertions.fail("Should not reach here — build should have failed");
    }
}
