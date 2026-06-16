package io.quarkiverse.jsonrpc.deployment;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusDevModeTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;

public class JsonRpcDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .withApplicationRoot(root -> root.addClasses(HelloResource.class));

    @Test
    public void testHotReloadViaWebSocket() throws Exception {
        String result1 = awaitJsonRpc("HelloResource#hello", 1, "Hello [", 60_000);
        Assertions.assertTrue(result1.startsWith("Hello ["), "Initial response: " + result1);

        devModeTest.modifySourceFile(HelloResource.class,
                s -> s.replace("return \"Hello [\"", "return \"Hola [\""));

        String result2 = awaitJsonRpc("HelloResource#hello", 2, "Hola [", 30_000);
        Assertions.assertTrue(result2.startsWith("Hola ["), "After hot reload: " + result2);
    }

    private String awaitJsonRpc(String method, int id, String expectedPrefix, long timeoutMs) throws Exception {
        String result = null;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                result = callJsonRpc(method, id);
            } catch (Exception e) {
                // App not ready or restarting
            }
            if (result != null && result.startsWith(expectedPrefix)) {
                return result;
            }
            Thread.sleep(1000);
        }
        Assertions.fail("No response matching '" + expectedPrefix + "' within " + timeoutMs + "ms");
        return null;
    }

    private String callJsonRpc(String method, int id) throws Exception {
        Vertx vertx = Vertx.vertx();
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            client.connect(8080, "localhost", "/json-rpc")
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            var ws = r.result();
                            ws.textMessageHandler(messages::add);
                            ws.writeTextMessage(
                                    JsonObject.of("jsonrpc", "2.0", "id", id, "method", method).encode());
                        }
                    });
            String response = messages.poll(5, TimeUnit.SECONDS);
            if (response == null) {
                return null;
            }
            JsonObject json = new JsonObject(response);
            if (json.getJsonObject("error") != null) {
                return null;
            }
            return json.getString("result");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
            vertx.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
