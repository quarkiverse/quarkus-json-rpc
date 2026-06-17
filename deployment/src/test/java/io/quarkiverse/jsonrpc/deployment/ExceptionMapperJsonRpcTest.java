package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.ExceptionMapperResource;
import io.quarkiverse.jsonrpc.app.MapperBrokenException;
import io.quarkiverse.jsonrpc.app.OrderNotFoundException;
import io.quarkiverse.jsonrpc.app.TestExceptionMapper;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ExceptionMapperJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(ExceptionMapperResource.class, OrderNotFoundException.class,
                        MapperBrokenException.class, TestExceptionMapper.class);
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

    @Test
    public void testBrokenMapperFallsBackToInternalError() throws Exception {
        JsonObject response = getJsonRpcRawResponse("ExceptionMapperResource#mapperThrows");
        JsonObject error = response.getJsonObject("error");
        Assertions.assertNotNull(error, "Expected error response");
        Assertions.assertEquals(-32603, error.getInteger("code"));
        Assertions.assertTrue(error.getString("message").contains("trigger broken mapper"));
    }

    @Test
    public void testSubscriptionErrorUsesCustomMapper() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 99,
                    "method", "ExceptionMapperResource#failingStream",
                    "params", JsonObject.of("orderId", "XYZ-789"));

            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            var ws = r.result();
                            ws.textMessageHandler(messages::add);
                            ws.writeTextMessage(request.encode());
                            connected.complete(null);
                        } else {
                            connected.completeExceptionally(r.cause());
                        }
                    });

            connected.get(5, TimeUnit.SECONDS);

            // Message 1: ack with subscription ID
            String ackMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(ackMsg, "Expected ack message");
            JsonObject ack = new JsonObject(ackMsg);
            Assertions.assertEquals(99, ack.getInteger("id"));
            String subscriptionId = ack.getString("result");
            Assertions.assertNotNull(subscriptionId, "Ack result should be a subscription ID");

            // Message 2: error notification with custom mapper code/data
            String errMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(errMsg, "Expected error notification");
            JsonObject errJson = new JsonObject(errMsg);
            Assertions.assertEquals("subscription", errJson.getString("method"));
            JsonObject errParams = errJson.getJsonObject("params");
            Assertions.assertEquals(subscriptionId, errParams.getString("subscription"));
            JsonObject error = errParams.getJsonObject("error");
            Assertions.assertNotNull(error, "Expected error object in params");
            Assertions.assertEquals(-40001, error.getInteger("code"));
            Assertions.assertEquals("Order not found", error.getString("message"));
            JsonObject data = error.getJsonObject("data");
            Assertions.assertNotNull(data, "Expected data field in subscription error");
            Assertions.assertEquals("XYZ-789", data.getString("orderId"));
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
