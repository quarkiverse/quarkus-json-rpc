package io.quarkiverse.jsonrpc.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;

public class WebDependencyLocatorTest extends JsClientTestBase {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class);
            })
            .overrideConfigKey("quarkus.json-rpc.js-client.enabled", "true");

    @Test
    public void testImportMapContainsJsonRpcMappings() throws Exception {
        String body = httpGet("/_importmap/generated_importmap.js");
        Assertions.assertTrue(body.contains("@quarkiverse/json-rpc"),
                "Import map should contain @quarkiverse/json-rpc mapping");
        Assertions.assertTrue(body.contains("/_static/quarkus-json-rpc/jsonrpc-client.js"),
                "Import map should map to the json-rpc client library");
        Assertions.assertTrue(body.contains("/_static/quarkus-json-rpc-api/jsonrpc-api.js"),
                "Import map should map to the json-rpc API proxy");
    }
}
