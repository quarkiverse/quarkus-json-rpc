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
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class BatchJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class);
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
