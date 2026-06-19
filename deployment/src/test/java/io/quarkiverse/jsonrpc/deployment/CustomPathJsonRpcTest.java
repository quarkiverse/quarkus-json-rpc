package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.CustomPathResource;
import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.json.JsonObject;

public class CustomPathJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, CustomPathResource.class);
            });

    @TestHTTPResource("custom-rpc")
    URI customRpcUri;

    @Test
    public void testDefaultPathStillWorks() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello");
        Assertions.assertTrue(result.startsWith("Hello [executor-thread-"), result);
    }

    @Test
    public void testDefaultPathWithParam() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello", Map.of("name", "Koos"));
        Assertions.assertTrue(result.startsWith("Hello Koos [executor-thread-"), result);
    }

    @Test
    public void testCustomPathHello() throws Exception {
        String result = getJsonRpcResponse(customRpcUri, "CustomPathResource#customHello");
        Assertions.assertTrue(result.startsWith("Hello from custom path [executor-thread-"), result);
    }

    @Test
    public void testCustomPathHelloWithParam() throws Exception {
        String result = getJsonRpcResponse(customRpcUri, "CustomPathResource#customHello",
                Map.of("name", "Piet"));
        Assertions.assertTrue(result.startsWith("Hello Piet from custom path [executor-thread-"), result);
    }

    @Test
    public void testCustomPathMethodNotCallableFromDefaultPath() throws Exception {
        JsonObject response = getJsonRpcRawResponse("CustomPathResource#customHello");
        JsonObject error = response.getJsonObject("error");
        Assertions.assertNotNull(error, "Expected error response when calling custom-path method from default path");
        Assertions.assertEquals(-32601, error.getInteger("code"));
        Assertions.assertTrue(error.getString("message").contains("not found"),
                "Error message should indicate method not found: " + error.getString("message"));
    }

    @Test
    public void testMultiStreamOverCustomPath() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "CustomPathResource#customStream");

            client.connect(customRpcUri.getPort(), customRpcUri.getHost(), customRpcUri.getPath())
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

            // Ack with subscription ID
            String ackMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(ackMsg, "Expected ack message");
            JsonObject ack = new JsonObject(ackMsg);
            Assertions.assertEquals(1, ack.getInteger("id"));
            String subscriptionId = ack.getString("result");
            Assertions.assertNotNull(subscriptionId, "Ack result should be a subscription ID");

            // 3 item notifications
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected item notification " + i);
                JsonObject json = new JsonObject(msg);
                Assertions.assertEquals("subscription", json.getString("method"));
                JsonObject params = json.getJsonObject("params");
                Assertions.assertEquals(subscriptionId, params.getString("subscription"));
                items.add(params.getString("result"));
            }
            Assertions.assertEquals(List.of("custom-0", "custom-1", "custom-2"), items);

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
}
