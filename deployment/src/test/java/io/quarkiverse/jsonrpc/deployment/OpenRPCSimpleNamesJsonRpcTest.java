package io.quarkiverse.jsonrpc.deployment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.BasePojo;
import io.quarkiverse.jsonrpc.app.ChildPojo;
import io.quarkiverse.jsonrpc.app.Pojo;
import io.quarkiverse.jsonrpc.app.Pojo2;
import io.quarkiverse.jsonrpc.app.PojoResource;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class OpenRPCSimpleNamesJsonRpcTest extends JsClientTestBase {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(PojoResource.class, Pojo.class, Pojo2.class,
                        BasePojo.class, ChildPojo.class);
            })
            .overrideConfigKey("quarkus.json-rpc.openrpc.schema-simple-names", "true");

    @Test
    public void testSchemaUsesSimpleNames() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject schemas = doc.getJsonObject("components").getJsonObject("schemas");
        assertNotNull(schemas);
        assertTrue(schemas.containsKey("Pojo"), "Schema keys should use simple names when configured");
        assertTrue(schemas.containsKey("Pojo2"), "Nested schema keys should also use simple names");
    }
}
