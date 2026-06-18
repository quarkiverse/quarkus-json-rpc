package io.quarkiverse.jsonrpc.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.FaultToleranceTimeoutResource;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class FaultToleranceTimeoutJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(FaultToleranceTimeoutResource.class));

    @Test
    public void testSmallRyeFaultToleranceTimeout() throws Exception {
        JsonObject response = getJsonRpcRawResponse("FaultToleranceTimeoutResource#slow");
        JsonObject error = response.getJsonObject("error");
        Assertions.assertNotNull(error, "Expected error response for @Timeout method");
        Assertions.assertEquals(-32002, error.getInteger("code"));
        Assertions.assertTrue(error.getString("message").contains("timed out"));
    }

    @Test
    public void testFastMethodSucceeds() throws Exception {
        String result = getJsonRpcResponse("FaultToleranceTimeoutResource#fast");
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.startsWith("Hello "));
    }
}
