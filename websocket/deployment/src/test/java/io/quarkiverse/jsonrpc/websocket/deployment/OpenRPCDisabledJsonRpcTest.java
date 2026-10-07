package io.quarkiverse.jsonrpc.websocket.deployment;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenRPCDisabledJsonRpcTest extends JsClientTestBase {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
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
