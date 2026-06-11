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
        String result1 = callJsonRpc("HelloResource#hello", 1);
        Assertions.assertTrue(result1.startsWith("Hello ["), "Initial response: " + result1);

        devModeTest.modifySourceFile(HelloResource.class,
                s -> s.replace("return \"Hello [\"", "return \"Hola [\""));

        String result2 = callJsonRpc("HelloResource#hello", 2);
        Assertions.assertTrue(result2.startsWith("Hola ["), "After hot reload: " + result2);
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
                        } else {
                            messages.add(JsonObject.of("error",
                                    JsonObject.of("message", r.cause().getMessage())).encode());
                        }
                    });
            String response = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response, "No response received");
            JsonObject json = new JsonObject(response);
            Assertions.assertNull(json.getJsonObject("error"), "Unexpected error: " + response);
            return json.getString("result");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
            vertx.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
