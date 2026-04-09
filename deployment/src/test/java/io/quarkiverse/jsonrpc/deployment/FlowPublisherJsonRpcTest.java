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

import io.quarkiverse.jsonrpc.app.FlowPublisherResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class FlowPublisherJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(FlowPublisherResource.class);
            });

    @Inject
    Vertx vertx;

    @TestHTTPResource("quarkus/json-rpc")
    URI jsonRpcUri;

    @Test
    public void testFlowPublisherStream() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "FlowPublisherResource#items");

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
                Assertions.assertEquals("2.0", json.getString("jsonrpc"));
                Assertions.assertFalse(json.containsKey("id"), "Notification must not have an id");
                Assertions.assertEquals("subscription", json.getString("method"));
                JsonObject params = json.getJsonObject("params");
                Assertions.assertEquals(subscriptionId, params.getString("subscription"));
                items.add(params.getString("result"));
            }
            Assertions.assertEquals(List.of("fp-0", "fp-1", "fp-2"), items);

            // Message 5: completion notification
            String completeMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(completeMsg, "Expected completion notification");
            JsonObject complete = new JsonObject(completeMsg);
            Assertions.assertEquals("2.0", complete.getString("jsonrpc"));
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
    public void testFlowPublisherStreamWithParam() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject params = JsonObject.of("prefix", "test");
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "FlowPublisherResource#items",
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
                Assertions.assertEquals("2.0", json.getString("jsonrpc"));
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
            Assertions.assertEquals("2.0", complete.getString("jsonrpc"));
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
    public void testFlowPublisherError() throws Exception {
        var client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 3, "method", "FlowPublisherResource#failing");

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
            Assertions.assertEquals("2.0", errJson.getString("jsonrpc"));
            Assertions.assertFalse(errJson.containsKey("id"), "Error notification must not have an id");
            Assertions.assertEquals("subscription", errJson.getString("method"));
            JsonObject errParams = errJson.getJsonObject("params");
            Assertions.assertEquals(subscriptionId, errParams.getString("subscription"));
            JsonObject error = errParams.getJsonObject("error");
            Assertions.assertNotNull(error, "Expected error object in params");
            Assertions.assertTrue(error.getString("message").contains("Flow.Publisher test error"),
                    "Expected 'Flow.Publisher test error' in message but got: " + error.getString("message"));
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
