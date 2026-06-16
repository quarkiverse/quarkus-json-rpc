package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkiverse.jsonrpc.app.MultiResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class BatchJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, MultiResource.class);
            });

    @Inject
    Vertx vertx;

    @TestHTTPResource("json-rpc")
    URI jsonRpcUri;

    @Test
    public void testBatchRequest() throws Exception {
        JsonArray batch = new JsonArray()
                .add(JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "HelloResource#hello"))
                .add(JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "HelloResource#helloNonBlocking"));

        JsonArray responses = sendBatch(batch);

        Assertions.assertEquals(2, responses.size());

        JsonObject r1 = findById(responses, 1);
        Assertions.assertNotNull(r1, "Response with id=1 not found");
        Assertions.assertTrue(r1.getString("result").startsWith("Hello [executor-thread-"));

        JsonObject r2 = findById(responses, 2);
        Assertions.assertNotNull(r2, "Response with id=2 not found");
        Assertions.assertTrue(r2.getString("result").startsWith("Hello [vert.x-eventloop-thread-"));
    }

    @Test
    public void testBatchWithParams() throws Exception {
        JsonArray batch = new JsonArray()
                .add(JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "HelloResource#hello",
                        "params", JsonObject.of("name", "Alice")))
                .add(JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "HelloResource#hello",
                        "params", JsonObject.of("name", "Bob")));

        JsonArray responses = sendBatch(batch);

        Assertions.assertEquals(2, responses.size());

        JsonObject r1 = findById(responses, 1);
        Assertions.assertTrue(r1.getString("result").startsWith("Hello Alice ["));

        JsonObject r2 = findById(responses, 2);
        Assertions.assertTrue(r2.getString("result").startsWith("Hello Bob ["));
    }

    @Test
    public void testBatchWithMethodNotFound() throws Exception {
        JsonArray batch = new JsonArray()
                .add(JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "HelloResource#hello"))
                .add(JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "NoSuchService#missing"));

        JsonArray responses = sendBatch(batch);

        Assertions.assertEquals(2, responses.size());

        JsonObject r1 = findById(responses, 1);
        Assertions.assertNotNull(r1.getString("result"));
        Assertions.assertNull(r1.getJsonObject("error"));

        JsonObject r2 = findById(responses, 2);
        Assertions.assertNull(r2.getString("result"));
        JsonObject error = r2.getJsonObject("error");
        Assertions.assertNotNull(error);
        Assertions.assertEquals(-32601, error.getInteger("code"));
    }

    @Test
    public void testSingleElementBatch() throws Exception {
        JsonArray batch = new JsonArray()
                .add(JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "HelloResource#hello"));

        JsonArray responses = sendBatch(batch);

        Assertions.assertEquals(1, responses.size());
        JsonObject r1 = responses.getJsonObject(0);
        Assertions.assertEquals(1, r1.getInteger("id"));
        Assertions.assertTrue(r1.getString("result").startsWith("Hello ["));
    }

    @Test
    public void testEmptyBatchReturnsError() throws Exception {
        String rawResponse = sendRaw("[]");
        JsonObject response = new JsonObject(rawResponse);
        Assertions.assertNotNull(response.getJsonObject("error"));
        Assertions.assertEquals(-32600, response.getJsonObject("error").getInteger("code"));
    }

    @Test
    public void testMalformedJsonReturnsParseError() throws Exception {
        String rawResponse = sendRaw("{not valid json");
        JsonObject response = new JsonObject(rawResponse);
        Assertions.assertNotNull(response.getJsonObject("error"));
        Assertions.assertEquals(-32700, response.getJsonObject("error").getInteger("code"));
    }

    @Test
    public void testBatchWithMultiStreaming() throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<WebSocket> connected = new CompletableFuture<>();

            JsonArray batch = new JsonArray()
                    .add(JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "HelloResource#hello"))
                    .add(JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "MultiResource#items"));

            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            var ws = r.result();
                            ws.textMessageHandler(messages::add);
                            ws.writeTextMessage(batch.encode());
                            connected.complete(ws);
                        } else {
                            connected.completeExceptionally(r.cause());
                        }
                    });

            connected.get(5, TimeUnit.SECONDS);

            // First message: the batch response array containing both results
            String batchMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(batchMsg, "Expected batch response");
            JsonArray responses = new JsonArray(batchMsg);
            Assertions.assertEquals(2, responses.size());

            JsonObject r1 = findById(responses, 1);
            Assertions.assertNotNull(r1, "Response with id=1 not found");
            Assertions.assertTrue(r1.getString("result").startsWith("Hello ["));

            JsonObject r2 = findById(responses, 2);
            Assertions.assertNotNull(r2, "Response with id=2 not found");
            String subscriptionId = r2.getString("result");
            Assertions.assertNotNull(subscriptionId, "Multi ack should contain a subscription ID");

            // Subsequent messages: subscription item notifications delivered individually
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected item notification " + i);
                JsonObject json = new JsonObject(msg);
                Assertions.assertEquals("2.0", json.getString("jsonrpc"));
                Assertions.assertFalse(json.containsKey("id"), "Notification must not have an id");
                Assertions.assertEquals("subscription", json.getString("method"));
                JsonObject params = json.getJsonObject("params");
                Assertions.assertEquals(subscriptionId, params.getString("subscription"));
                items.add(params.getString("result"));
            }
            Assertions.assertEquals(List.of("item-0", "item-1", "item-2"), items);

            // Completion notification
            String completeMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(completeMsg, "Expected completion notification");
            JsonObject complete = new JsonObject(completeMsg);
            Assertions.assertEquals("subscription", complete.getString("method"));
            JsonObject completeParams = complete.getJsonObject("params");
            Assertions.assertEquals(subscriptionId, completeParams.getString("subscription"));
            Assertions.assertTrue(completeParams.getBoolean("complete"));
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testBatchWithInvalidElement() throws Exception {
        // A batch where one element is a bare number (valid JSON but not a valid JSON-RPC request)
        String rawBatch = "[42, {\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"HelloResource#hello\"}]";

        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            r.result().textMessageHandler(queue::add);
                            r.result().writeTextMessage(rawBatch);
                        } else {
                            throw new IllegalStateException(r.cause());
                        }
                    });
            String response = queue.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response, "No response received within timeout");

            JsonArray responses = new JsonArray(response);
            Assertions.assertEquals(2, responses.size());

            // One response should be an error for the invalid element
            boolean hasInvalidRequestError = false;
            boolean hasSuccessResult = false;
            for (int i = 0; i < responses.size(); i++) {
                JsonObject obj = responses.getJsonObject(i);
                if (obj.getJsonObject("error") != null
                        && obj.getJsonObject("error").getInteger("code") == -32600) {
                    hasInvalidRequestError = true;
                }
                if (obj.getString("result") != null
                        && obj.getString("result").startsWith("Hello [")) {
                    hasSuccessResult = true;
                }
            }
            Assertions.assertTrue(hasInvalidRequestError, "Expected an invalid request error for the bare number");
            Assertions.assertTrue(hasSuccessResult, "Expected a success result for the valid request");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    private JsonArray sendBatch(JsonArray batch) throws Exception {
        String raw = sendRaw(batch.encode());
        return new JsonArray(raw);
    }

    private String sendRaw(String message) throws Exception {
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
            Assertions.assertNotNull(response, "No response received within timeout");
            return response;
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    private static JsonObject findById(JsonArray responses, int id) {
        for (int i = 0; i < responses.size(); i++) {
            JsonObject obj = responses.getJsonObject(i);
            if (obj.getInteger("id") == id) {
                return obj;
            }
        }
        return null;
    }
}
