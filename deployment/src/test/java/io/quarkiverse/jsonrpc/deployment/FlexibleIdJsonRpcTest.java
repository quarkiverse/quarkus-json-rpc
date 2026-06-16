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

public class FlexibleIdJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(HelloResource.class));

    @Inject
    Vertx vertx;

    @TestHTTPResource("json-rpc")
    URI jsonRpcUri;

    @Test
    public void testStringId() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", "req-abc", "method", "HelloResource#hello");

        JsonObject response = sendAndReceive(request);

        Assertions.assertEquals("req-abc", response.getString("id"));
        Assertions.assertTrue(response.getString("result").startsWith("Hello ["));
    }

    @Test
    public void testLargeNumberId() throws Exception {
        long largeId = 9007199254740993L;
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", largeId, "method", "HelloResource#hello");

        JsonObject response = sendAndReceive(request);

        Assertions.assertEquals(largeId, response.getLong("id"));
        Assertions.assertTrue(response.getString("result").startsWith("Hello ["));
    }

    @Test
    public void testNullId() throws Exception {
        String rawRequest = "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"HelloResource#hello\"}";

        String rawResponse = sendRaw(rawRequest);
        JsonObject response = new JsonObject(rawResponse);

        Assertions.assertNull(response.getValue("id"));
        Assertions.assertTrue(response.getString("result").startsWith("Hello ["));
    }

    @Test
    public void testStringIdWithParams() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", "named-req",
                "method", "HelloResource#hello", "params", JsonObject.of("name", "Alice"));

        JsonObject response = sendAndReceive(request);

        Assertions.assertEquals("named-req", response.getString("id"));
        Assertions.assertTrue(response.getString("result").startsWith("Hello Alice ["));
    }

    @Test
    public void testStringIdMethodNotFound() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", "err-1", "method", "NoSuch#method");

        JsonObject response = sendAndReceive(request);

        Assertions.assertEquals("err-1", response.getString("id"));
        Assertions.assertNotNull(response.getJsonObject("error"));
        Assertions.assertEquals(-32601, response.getJsonObject("error").getInteger("code"));
    }

    @Test
    public void testBatchWithMixedIdTypes() throws Exception {
        JsonArray batch = new JsonArray()
                .add(JsonObject.of("jsonrpc", "2.0", "id", "str-1", "method", "HelloResource#hello"))
                .add(JsonObject.of("jsonrpc", "2.0", "id", 42, "method", "HelloResource#hello"));

        String rawResponse = sendRaw(batch.encode());
        JsonArray responses = new JsonArray(rawResponse);
        Assertions.assertEquals(2, responses.size());

        boolean foundString = false;
        boolean foundInt = false;
        for (int i = 0; i < responses.size(); i++) {
            JsonObject r = responses.getJsonObject(i);
            Object id = r.getValue("id");
            if ("str-1".equals(id)) {
                foundString = true;
            } else if (Integer.valueOf(42).equals(id)) {
                foundInt = true;
            }
        }
        Assertions.assertTrue(foundString, "Expected response with string id 'str-1'");
        Assertions.assertTrue(foundInt, "Expected response with int id 42");
    }

    private JsonObject sendAndReceive(JsonObject request) throws Exception {
        return new JsonObject(sendRaw(request.encode()));
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
}
