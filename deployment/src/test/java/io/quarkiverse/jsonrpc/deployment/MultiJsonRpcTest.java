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

import io.quarkiverse.jsonrpc.app.MultiResource;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MultiJsonRpcTest {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(root -> {
                root.addClasses(MultiResource.class);
            });

    @Inject
    Vertx vertx;

    @TestHTTPResource("quarkus/json-rpc")
    URI jsonRpcUri;

    @Test
    public void testMultiStream() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "MultiResource#items");

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
            Assertions.assertEquals(1, ack.getInteger("id"));
            String subscriptionId = ack.getString("result");
            Assertions.assertNotNull(subscriptionId, "Ack result should be a subscription ID");

            // Messages 2-4: subscription item notifications
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected item notification " + i);
                JsonObject json = new JsonObject(msg);
                Assertions.assertFalse(json.containsKey("id"), "Notification must not have an id");
                Assertions.assertEquals("subscription", json.getString("method"));
                JsonObject params = json.getJsonObject("params");
                Assertions.assertEquals(subscriptionId, params.getString("subscription"));
                items.add(params.getString("result"));
            }
            Assertions.assertEquals(List.of("item-0", "item-1", "item-2"), items);

            // Message 5: completion notification
            String completeMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(completeMsg, "Expected completion notification");
            JsonObject complete = new JsonObject(completeMsg);
            Assertions.assertFalse(complete.containsKey("id"), "Completion notification must not have an id");
            Assertions.assertEquals("subscription", complete.getString("method"));
            JsonObject completeParams = complete.getJsonObject("params");
            Assertions.assertEquals(subscriptionId, completeParams.getString("subscription"));
            Assertions.assertTrue(completeParams.getBoolean("complete"));
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testMultiStreamWithParam() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject params = JsonObject.of("prefix", "test");
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "MultiResource#items",
                    "params", params);

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
            Assertions.assertEquals(2, ack.getInteger("id"));
            String subscriptionId = ack.getString("result");
            Assertions.assertNotNull(subscriptionId, "Ack result should be a subscription ID");

            // Messages 2-4: subscription item notifications
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected item notification " + i);
                JsonObject json = new JsonObject(msg);
                Assertions.assertFalse(json.containsKey("id"), "Notification must not have an id");
                Assertions.assertEquals("subscription", json.getString("method"));
                JsonObject notifParams = json.getJsonObject("params");
                Assertions.assertEquals(subscriptionId, notifParams.getString("subscription"));
                items.add(notifParams.getString("result"));
            }
            Assertions.assertEquals(List.of("test-0", "test-1", "test-2"), items);

            // Message 5: completion notification
            String completeMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(completeMsg, "Expected completion notification");
            JsonObject complete = new JsonObject(completeMsg);
            Assertions.assertFalse(complete.containsKey("id"), "Completion notification must not have an id");
            Assertions.assertEquals("subscription", complete.getString("method"));
            JsonObject completeParams = complete.getJsonObject("params");
            Assertions.assertEquals(subscriptionId, completeParams.getString("subscription"));
            Assertions.assertTrue(completeParams.getBoolean("complete"));
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testMultiError() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 3, "method", "MultiResource#failing");

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
            Assertions.assertEquals(3, ack.getInteger("id"));
            String subscriptionId = ack.getString("result");
            Assertions.assertNotNull(subscriptionId, "Ack result should be a subscription ID");

            // Message 2: error notification
            String errMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(errMsg, "Expected error notification");
            JsonObject errJson = new JsonObject(errMsg);
            Assertions.assertFalse(errJson.containsKey("id"), "Error notification must not have an id");
            Assertions.assertEquals("subscription", errJson.getString("method"));
            JsonObject errParams = errJson.getJsonObject("params");
            Assertions.assertEquals(subscriptionId, errParams.getString("subscription"));
            JsonObject error = errParams.getJsonObject("error");
            Assertions.assertNotNull(error, "Expected error object in params");
            Assertions.assertTrue(error.getString("message").contains("Multi test error"),
                    "Expected 'Multi test error' in message but got: " + error.getString("message"));
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testUnsubscribe() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<WebSocket> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 4, "method", "MultiResource#ticking");

            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            var ws = r.result();
                            ws.textMessageHandler(messages::add);
                            ws.writeTextMessage(request.encode());
                            connected.complete(ws);
                        } else {
                            connected.completeExceptionally(r.cause());
                        }
                    });

            WebSocket ws = connected.get(5, TimeUnit.SECONDS);

            // Message 1: ack with subscription ID
            String ackMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(ackMsg, "Expected ack message");
            JsonObject ack = new JsonObject(ackMsg);
            Assertions.assertEquals(4, ack.getInteger("id"));
            String subscriptionId = ack.getString("result");
            Assertions.assertNotNull(subscriptionId, "Ack result should be a subscription ID");

            // Wait for at least 2 items
            for (int i = 0; i < 2; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected item notification " + i);
                JsonObject json = new JsonObject(msg);
                Assertions.assertEquals("subscription", json.getString("method"));
            }

            // Send unsubscribe
            JsonObject unsubRequest = JsonObject.of("jsonrpc", "2.0", "id", 5, "method", "unsubscribe",
                    "params", new JsonArray().add(subscriptionId));
            ws.writeTextMessage(unsubRequest.encode());

            // Expect unsubscribe response: result = true
            String unsubMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(unsubMsg, "Expected unsubscribe response");
            JsonObject unsub = new JsonObject(unsubMsg);
            Assertions.assertEquals(5, unsub.getInteger("id"));
            Assertions.assertTrue(unsub.getBoolean("result"), "Unsubscribe should return true");

            // Drain any items that arrived between unsubscribe request and cancellation
            messages.clear();

            // Verify no more items arrive after unsubscribe
            Thread.sleep(300);
            Assertions.assertTrue(messages.isEmpty(), "No more items should arrive after unsubscribe");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testUnsubscribeUnknown() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<WebSocket> connected = new CompletableFuture<>();

            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            var ws = r.result();
                            ws.textMessageHandler(messages::add);
                            connected.complete(ws);
                        } else {
                            connected.completeExceptionally(r.cause());
                        }
                    });

            WebSocket ws = connected.get(5, TimeUnit.SECONDS);

            // Send unsubscribe for non-existent subscription
            JsonObject unsubRequest = JsonObject.of("jsonrpc", "2.0", "id", 6, "method", "unsubscribe",
                    "params", new JsonArray().add("non-existent-id"));
            ws.writeTextMessage(unsubRequest.encode());

            // Expect response: result = false
            String unsubMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(unsubMsg, "Expected unsubscribe response");
            JsonObject unsub = new JsonObject(unsubMsg);
            Assertions.assertEquals(6, unsub.getInteger("id"));
            Assertions.assertFalse(unsub.getBoolean("result"), "Unsubscribe of unknown ID should return false");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
