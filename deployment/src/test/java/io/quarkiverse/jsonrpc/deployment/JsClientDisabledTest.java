package io.quarkiverse.jsonrpc.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;

public class JsClientDisabledTest extends JsClientTestBase {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class);
            })
            .overrideConfigKey("quarkus.json-rpc.client.enabled", "false");

    @Test
    public void testClientLibraryNotServedWhenDisabled() throws Exception {
        int status = httpStatus("/_static/quarkus-json-rpc/jsonrpc-client.js");
        Assertions.assertNotEquals(200, status,
                "Client library should not be served when client generation is disabled");
    }

    @Test
    public void testTypedProxyNotServedWhenDisabled() throws Exception {
        int status = httpStatus("/_static/quarkus-json-rpc-api/jsonrpc-api.js");
        Assertions.assertNotEquals(200, status,
                "Typed proxy should not be served when client generation is disabled");
    }
}
