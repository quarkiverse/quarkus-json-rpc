package io.quarkiverse.json.rpc.it;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
public class JsonRpcWebSocketTest {

    @Inject
    Vertx vertx;

    @TestHTTPResource("quarkus/json-rpc")
    URI jsonRpcUri;

    @Test
    public void testNoParams() throws Exception {
        JsonObject json = sendJsonRpc(1, "GreetingJsonRpcEndpoint#greet");
        Assertions.assertEquals("Hello from JSON-RPC", json.getString("result"));
    }

    @Test
    public void testWithNamedParam() throws Exception {
        JsonObject params = JsonObject.of("name", "World");
        JsonObject json = sendJsonRpc(2, "GreetingJsonRpcEndpoint#greet", params);
        Assertions.assertEquals("Hello World", json.getString("result"));
    }

    @Test
    public void testUniReturn() throws Exception {
        JsonObject params = JsonObject.of("name", "Quarkus");
        JsonObject json = sendJsonRpc(3, "GreetingJsonRpcEndpoint#greetAsync", params);
        Assertions.assertEquals("Hello async Quarkus", json.getString("result"));
    }

    @Test
    public void testWithPositionalParam() throws Exception {
        JsonArray params = JsonArray.of("Positional");
        JsonObject json = sendJsonRpc(4, "GreetingJsonRpcEndpoint#greet", params);
        Assertions.assertEquals("Hello Positional", json.getString("result"));
    }

    @Test
    public void testMethodNotFound() throws Exception {
        JsonObject json = sendJsonRpc(5, "GreetingJsonRpcEndpoint#nonExistent");
        Assertions.assertNull(json.getValue("result"));
        JsonObject error = json.getJsonObject("error");
        Assertions.assertNotNull(error, "Expected error response for non-existent method");
        Assertions.assertEquals(-32601, error.getInteger("code"));
    }

    private JsonObject sendJsonRpc(int id, String method) throws Exception {
        return sendJsonRpc(id, method, (Object) null);
    }

    private JsonObject sendJsonRpc(int id, String method, Object params) throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", id, "method", method);
            if (params != null) {
                request.put("params", params);
            }

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
            String response = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response, "No response received within timeout");
            JsonObject json = new JsonObject(response);
            Assertions.assertEquals(id, json.getInteger("id"));
            return json;
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
