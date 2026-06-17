package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.ExceptionMapperResource;
import io.quarkiverse.jsonrpc.app.OrderNotFoundException;
import io.quarkiverse.jsonrpc.app.TestExceptionMapper;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ExceptionMapperJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(ExceptionMapperResource.class, OrderNotFoundException.class, TestExceptionMapper.class);
            });

    @Test
    public void testCustomExceptionMappedWithCodeMessageAndData() throws Exception {
        JsonObject response = getJsonRpcRawResponse("ExceptionMapperResource#orderLookup",
                Map.of("orderId", "ABC-123"));
        JsonObject error = response.getJsonObject("error");
        Assertions.assertNotNull(error, "Expected error response");
        Assertions.assertEquals(-40001, error.getInteger("code"));
        Assertions.assertEquals("Order not found", error.getString("message"));

        JsonObject data = error.getJsonObject("data");
        Assertions.assertNotNull(data, "Expected data field in error response");
        Assertions.assertEquals("ABC-123", data.getString("orderId"));
    }

    @Test
    public void testUnmappedExceptionFallsBackToInternalError() throws Exception {
        JsonObject response = getJsonRpcRawResponse("ExceptionMapperResource#unmappedException");
        JsonObject error = response.getJsonObject("error");
        Assertions.assertNotNull(error, "Expected error response");
        Assertions.assertEquals(-32603, error.getInteger("code"));
        Assertions.assertTrue(error.getString("message").contains("something went wrong"));
        Assertions.assertNull(error.getValue("data"), "Expected no data field for unmapped exception");
    }
}
