package io.quarkiverse.jsonrpc.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.RunOnVirtualThreadNonBlockingResource;
import io.quarkus.test.QuarkusUnitTest;

public class RunOnVirtualThreadNonBlockingValidationTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(RunOnVirtualThreadNonBlockingResource.class);
            })
            .assertException(t -> {
                Assertions.assertTrue(t instanceof IllegalArgumentException,
                        "Expected IllegalArgumentException but got: " + t.getClass().getName());
                Assertions.assertTrue(
                        t.getMessage().contains("cannot be annotated with both @RunOnVirtualThread and @NonBlocking"),
                        "Expected message about conflicting annotations but got: " + t.getMessage());
            });

    @Test
    public void testValidationFails() {
        Assertions.fail("Should not reach here — build should have failed");
    }
}
