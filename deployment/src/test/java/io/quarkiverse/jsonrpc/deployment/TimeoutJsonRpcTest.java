package io.quarkiverse.jsonrpc.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.TimeoutResource;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class TimeoutJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(TimeoutResource.class))
            .overrideConfigKey("quarkus.json-rpc.method-timeout", "1s");

    @Test
    public void testSlowMethodTimesOut() throws Exception {
        JsonObject response = getJsonRpcRawResponse("TimeoutResource#slow");
        JsonObject error = response.getJsonObject("error");
        Assertions.assertNotNull(error, "Expected error response for timed-out method");
        Assertions.assertEquals(-32002, error.getInteger("code"));
        Assertions.assertTrue(error.getString("message").contains("timed out"));
    }

    @Test
    public void testFastMethodSucceeds() throws Exception {
        String result = getJsonRpcResponse("TimeoutResource#fast");
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.startsWith("Hello "));
    }
}
