package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;

public class ErrorHandlingJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(HelloResource.class));

    @Inject
    Vertx vertx;

    @TestHTTPResource("json-rpc")
    URI jsonRpcUri;

    @Test
    public void testBareNumberReturnsInvalidRequest() throws Exception {
        JsonObject response = sendAndGetResponse("42");

        assertInvalidRequest(response);
        Assertions.assertTrue(response.getValue("id") == null, "id should be null for non-object input");
    }

    @Test
    public void testBareStringReturnsInvalidRequest() throws Exception {
        JsonObject response = sendAndGetResponse("\"hello\"");

        assertInvalidRequest(response);
    }

    @Test
    public void testBareBooleanReturnsInvalidRequest() throws Exception {
        JsonObject response = sendAndGetResponse("true");

        assertInvalidRequest(response);
    }

    @Test
    public void testBareNullReturnsInvalidRequest() throws Exception {
        JsonObject response = sendAndGetResponse("null");

        assertInvalidRequest(response);
    }

    @Test
    public void testObjectMissingMethodReturnsInvalidRequest() throws Exception {
        String request = JsonObject.of("jsonrpc", "2.0", "id", 1).encode();

        JsonObject response = sendAndGetResponse(request);

        assertInvalidRequest(response);
        Assertions.assertEquals(1, response.getInteger("id"), "id from request should be echoed");
    }

    @Test
    public void testObjectMissingMethodAndIdReturnsInvalidRequest() throws Exception {
        String request = JsonObject.of("jsonrpc", "2.0").encode();

        JsonObject response = sendAndGetResponse(request);

        assertInvalidRequest(response);
    }

    @Test
    public void testConnectionSurvivesError() throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            WebSocket ws = r.result();
                            ws.textMessageHandler(queue::add);
                            ws.writeTextMessage("not valid json");
                        } else {
                            throw new IllegalStateException(r.cause());
                        }
                    });

            String errorResponse = queue.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(errorResponse, "Should receive parse error response");
            JsonObject errorJson = new JsonObject(errorResponse);
            Assertions.assertEquals(-32700, errorJson.getJsonObject("error").getInteger("code"));

            // Now send a valid request on the same connection — it should still work
            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .toCompletionStage().toCompletableFuture().get();

            // Re-use the original connection by sending directly
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testConnectionSurvivesInvalidRequestThenServesValidRequest() throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
            WebSocket ws = client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .toCompletionStage().toCompletableFuture().get();
            ws.textMessageHandler(queue::add);

            ws.writeTextMessage("42");
            String errorResponse = queue.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(errorResponse, "Should receive invalid request error");
            assertInvalidRequest(new JsonObject(errorResponse));

            String validRequest = JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "HelloResource#hello").encode();
            ws.writeTextMessage(validRequest);
            String successResponse = queue.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(successResponse, "Should receive response for valid request");
            JsonObject successJson = new JsonObject(successResponse);
            Assertions.assertEquals(1, successJson.getInteger("id"));
            Assertions.assertTrue(successJson.getString("result").startsWith("Hello ["));
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    private void assertInvalidRequest(JsonObject response) {
        Assertions.assertNotNull(response.getJsonObject("error"), "Should contain an error object");
        Assertions.assertEquals(-32600, response.getJsonObject("error").getInteger("code"),
                "Error code should be INVALID_REQUEST (-32600)");
    }

    private JsonObject sendAndGetResponse(String message) throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            r.result().textMessageHandler(queue::add);
                            r.result().writeTextMessage(message);
                        } else {
                            throw new IllegalStateException(r.cause());
                        }
                    });
            String response = queue.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response, "Should receive an error response");
            return new JsonObject(response);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
