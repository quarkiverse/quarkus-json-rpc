package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.BroadcastResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class BroadcastJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(BroadcastResource.class);
            });

    @Inject
    Vertx vertx;

    @TestHTTPResource("quarkus/json-rpc")
    URI jsonRpcUri;

    @Test
    public void testBroadcastToMultipleClients() throws Exception {
        WebSocketClient client1 = vertx.createWebSocketClient();
        WebSocketClient client2 = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages1 = new LinkedBlockingDeque<>();
            LinkedBlockingDeque<String> messages2 = new LinkedBlockingDeque<>();

            // Connect client 1
            CompletableFuture<WebSocket> connected1 = new CompletableFuture<>();
            client1.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            r.result().textMessageHandler(messages1::add);
                            connected1.complete(r.result());
                        } else {
                            connected1.completeExceptionally(r.cause());
                        }
                    });
            WebSocket ws1 = connected1.get(5, TimeUnit.SECONDS);

            // Connect client 2
            CompletableFuture<WebSocket> connected2 = new CompletableFuture<>();
            client2.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            r.result().textMessageHandler(messages2::add);
                            connected2.complete(r.result());
                        } else {
                            connected2.completeExceptionally(r.cause());
                        }
                    });
            connected2.get(5, TimeUnit.SECONDS);

            // Trigger broadcast via client 1
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 1,
                    "method", "BroadcastResource#triggerBroadcast",
                    "params", JsonObject.of("method", "testEvent", "message", "hello everyone"));
            ws1.writeTextMessage(request.encode());

            // Client 1 should get the RPC response AND the broadcast notification
            String response1 = messages1.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response1, "Client 1 should receive a message");
            String msg1b = messages1.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(msg1b, "Client 1 should receive a second message");

            // One of the messages is the RPC response, the other is the broadcast notification
            JsonObject rpcResponse = null;
            JsonObject broadcastNotif1 = null;
            for (String msg : new String[] { response1, msg1b }) {
                JsonObject json = new JsonObject(msg);
                if (json.containsKey("id")) {
                    rpcResponse = json;
                } else {
                    broadcastNotif1 = json;
                }
            }

            Assertions.assertNotNull(rpcResponse, "Expected RPC response");
            Assertions.assertEquals(1, rpcResponse.getInteger("id"));
            Assertions.assertEquals("broadcast sent", rpcResponse.getString("result"));

            Assertions.assertNotNull(broadcastNotif1, "Expected broadcast notification for client 1");
            Assertions.assertEquals("2.0", broadcastNotif1.getString("jsonrpc"));
            Assertions.assertFalse(broadcastNotif1.containsKey("id"), "Notification must not have an id");
            Assertions.assertEquals("testEvent", broadcastNotif1.getString("method"));
            Assertions.assertEquals("hello everyone", broadcastNotif1.getJsonObject("params").getString("result"));

            // Client 2 should get the broadcast notification
            String notif2 = messages2.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(notif2, "Client 2 should receive broadcast notification");
            JsonObject broadcastNotif2 = new JsonObject(notif2);
            Assertions.assertEquals("2.0", broadcastNotif2.getString("jsonrpc"));
            Assertions.assertFalse(broadcastNotif2.containsKey("id"), "Notification must not have an id");
            Assertions.assertEquals("testEvent", broadcastNotif2.getString("method"));
            Assertions.assertEquals("hello everyone", broadcastNotif2.getJsonObject("params").getString("result"));
        } finally {
            client1.close().toCompletionStage().toCompletableFuture().get();
            client2.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testSessionCount() throws Exception {
        WebSocketClient client1 = vertx.createWebSocketClient();
        WebSocketClient client2 = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages1 = new LinkedBlockingDeque<>();

            // Connect client 1
            CompletableFuture<WebSocket> connected1 = new CompletableFuture<>();
            client1.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            r.result().textMessageHandler(messages1::add);
                            connected1.complete(r.result());
                        } else {
                            connected1.completeExceptionally(r.cause());
                        }
                    });
            WebSocket ws1 = connected1.get(5, TimeUnit.SECONDS);

            // Connect client 2
            CompletableFuture<WebSocket> connected2 = new CompletableFuture<>();
            client2.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            connected2.complete(r.result());
                        } else {
                            connected2.completeExceptionally(r.cause());
                        }
                    });
            connected2.get(5, TimeUnit.SECONDS);

            // Ask for session count via client 1
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 1,
                    "method", "BroadcastResource#getSessionCount");
            ws1.writeTextMessage(request.encode());

            String response = messages1.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response, "Expected response");
            JsonObject json = new JsonObject(response);
            Assertions.assertEquals(1, json.getInteger("id"));
            int count = json.getInteger("result");
            Assertions.assertTrue(count >= 2, "Should have at least 2 connected sessions, got " + count);
        } finally {
            client1.close().toCompletionStage().toCompletableFuture().get();
            client2.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testSendToKnownSession() throws Exception {
        WebSocketClient client1 = vertx.createWebSocketClient();
        WebSocketClient client2 = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages1 = new LinkedBlockingDeque<>();
            LinkedBlockingDeque<String> messages2 = new LinkedBlockingDeque<>();

            // Connect client 1
            CompletableFuture<WebSocket> connected1 = new CompletableFuture<>();
            client1.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            r.result().textMessageHandler(messages1::add);
                            connected1.complete(r.result());
                        } else {
                            connected1.completeExceptionally(r.cause());
                        }
                    });
            WebSocket ws1 = connected1.get(5, TimeUnit.SECONDS);

            // Connect client 2
            CompletableFuture<WebSocket> connected2 = new CompletableFuture<>();
            client2.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            r.result().textMessageHandler(messages2::add);
                            connected2.complete(r.result());
                        } else {
                            connected2.completeExceptionally(r.cause());
                        }
                    });
            connected2.get(5, TimeUnit.SECONDS);

            // Get all session IDs via client 1
            JsonObject getSessionsReq = JsonObject.of("jsonrpc", "2.0", "id", 1,
                    "method", "BroadcastResource#getSessionIds");
            ws1.writeTextMessage(getSessionsReq.encode());

            String sessionsResponse = messages1.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(sessionsResponse, "Expected session IDs response");
            JsonObject sessionsJson = new JsonObject(sessionsResponse);
            JsonArray sessionIds = sessionsJson.getJsonArray("result");
            Assertions.assertTrue(sessionIds.size() >= 2, "Should have at least 2 sessions");

            // Pick one session ID and send a targeted message
            String targetSessionId = sessionIds.getString(0);
            JsonObject sendReq = JsonObject.of("jsonrpc", "2.0", "id", 2,
                    "method", "BroadcastResource#sendToSession",
                    "params", JsonObject.of("sessionId", targetSessionId, "method", "targeted", "message", "just for you"));
            ws1.writeTextMessage(sendReq.encode());

            // Client 1 gets the RPC response, and possibly also the targeted notification
            // (if client 1's session was the one picked). Collect messages from both queues.
            String msg1a = messages1.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(msg1a, "Expected at least one message on client 1");

            // Separate RPC response from notifications across both clients
            JsonObject rpcResponse = null;
            JsonObject targetedNotif = null;

            // Check messages on client 1 (may have RPC response + notification)
            for (String raw = msg1a; raw != null; raw = messages1.poll(2, TimeUnit.SECONDS)) {
                JsonObject json = new JsonObject(raw);
                if (json.containsKey("id")) {
                    rpcResponse = json;
                } else {
                    targetedNotif = json;
                }
            }

            // Check messages on client 2 (may have the notification)
            String msg2 = messages2.poll(2, TimeUnit.SECONDS);
            if (msg2 != null) {
                JsonObject json = new JsonObject(msg2);
                if (!json.containsKey("id")) {
                    // Only one client should get the notification
                    Assertions.assertNull(targetedNotif, "Only the targeted client should receive notification");
                    targetedNotif = json;
                }
            }

            // Verify RPC response
            Assertions.assertNotNull(rpcResponse, "Expected RPC response");
            Assertions.assertEquals(2, rpcResponse.getInteger("id"));
            Assertions.assertTrue(rpcResponse.getBoolean("result"), "Send to known session should return true");

            // Verify exactly one client received the notification
            Assertions.assertNotNull(targetedNotif, "Targeted client should receive notification");
            Assertions.assertEquals("targeted", targetedNotif.getString("method"));
            Assertions.assertEquals("just for you", targetedNotif.getJsonObject("params").getString("result"));
            Assertions.assertFalse(targetedNotif.containsKey("id"), "Notification must not have an id");
        } finally {
            client1.close().toCompletionStage().toCompletableFuture().get();
            client2.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testSendToUnknownSession() throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();

            CompletableFuture<WebSocket> connected = new CompletableFuture<>();
            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            r.result().textMessageHandler(messages::add);
                            connected.complete(r.result());
                        } else {
                            connected.completeExceptionally(r.cause());
                        }
                    });
            WebSocket ws = connected.get(5, TimeUnit.SECONDS);

            // Send to non-existent session
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 1,
                    "method", "BroadcastResource#sendToSession",
                    "params", JsonObject.of("sessionId", "non-existent", "method", "test", "message", "hello"));
            ws.writeTextMessage(request.encode());

            String response = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response, "Expected response");
            JsonObject json = new JsonObject(response);
            Assertions.assertEquals(1, json.getInteger("id"));
            Assertions.assertFalse(json.getBoolean("result"), "Send to unknown session should return false");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
