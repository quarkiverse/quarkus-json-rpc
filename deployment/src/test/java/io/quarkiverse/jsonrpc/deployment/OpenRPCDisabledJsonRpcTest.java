package io.quarkiverse.jsonrpc.deployment;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;

public class OpenRPCDisabledJsonRpcTest extends JsClientTestBase {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class);
            })
            .overrideConfigKey("quarkus.json-rpc.openrpc.enabled", "false");

    @Test
    public void testOpenRPCNotServedWhenDisabled() throws Exception {
        int status = httpStatus("/json-rpc/openrpc.json");
        assertNotEquals(200, status, "OpenRPC document should not be served when disabled");
    }
}
