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

public class NotificationJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(HelloResource.class));

    @Inject
    Vertx vertx;

    @TestHTTPResource("json-rpc")
    URI jsonRpcUri;

    @Test
    public void testNotificationNoResponse() throws Exception {
        JsonObject notification = JsonObject.of("jsonrpc", "2.0", "method", "HelloResource#hello");

        String response = sendAndWaitForNoResponse(notification.encode());

        Assertions.assertNull(response, "Server must not reply to a notification");
    }

    @Test
    public void testNotificationWithParams() throws Exception {
        JsonObject notification = JsonObject.of("jsonrpc", "2.0", "method", "HelloResource#hello",
                "params", JsonObject.of("name", "Alice"));

        String response = sendAndWaitForNoResponse(notification.encode());

        Assertions.assertNull(response, "Server must not reply to a notification even with params");
    }

    @Test
    public void testNotificationUnknownMethodNoResponse() throws Exception {
        JsonObject notification = JsonObject.of("jsonrpc", "2.0", "method", "NoSuch#method");

        String response = sendAndWaitForNoResponse(notification.encode());

        Assertions.assertNull(response, "Server must not reply to a notification for an unknown method");
    }

    @Test
    public void testBatchMixedNotificationsAndRequests() throws Exception {
        JsonArray batch = new JsonArray()
                .add(JsonObject.of("jsonrpc", "2.0", "method", "HelloResource#hello"))
                .add(JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "HelloResource#hello"))
                .add(JsonObject.of("jsonrpc", "2.0", "method", "HelloResource#hello",
                        "params", JsonObject.of("name", "Bob")))
                .add(JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "HelloResource#hello",
                        "params", JsonObject.of("name", "Carol")));

        String rawResponse = sendRaw(batch.encode());
        JsonArray responses = new JsonArray(rawResponse);

        Assertions.assertEquals(2, responses.size(), "Only non-notification requests should have responses");

        boolean foundId1 = false;
        boolean foundId2 = false;
        for (int i = 0; i < responses.size(); i++) {
            JsonObject r = responses.getJsonObject(i);
            int id = r.getInteger("id");
            if (id == 1) {
                foundId1 = true;
                Assertions.assertTrue(r.getString("result").startsWith("Hello ["));
            } else if (id == 2) {
                foundId2 = true;
                Assertions.assertTrue(r.getString("result").startsWith("Hello Carol ["));
            }
        }
        Assertions.assertTrue(foundId1, "Expected response for request id 1");
        Assertions.assertTrue(foundId2, "Expected response for request id 2");
    }

    @Test
    public void testBatchAllNotifications() throws Exception {
        JsonArray batch = new JsonArray()
                .add(JsonObject.of("jsonrpc", "2.0", "method", "HelloResource#hello"))
                .add(JsonObject.of("jsonrpc", "2.0", "method", "HelloResource#hello",
                        "params", JsonObject.of("name", "Dave")));

        String response = sendAndWaitForNoResponse(batch.encode());

        Assertions.assertNull(response, "Server must not reply when batch contains only notifications");
    }

    @Test
    public void testNullIdIsNotNotification() throws Exception {
        String rawRequest = "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"HelloResource#hello\"}";

        String rawResponse = sendRaw(rawRequest);
        JsonObject response = new JsonObject(rawResponse);

        Assertions.assertTrue(response.containsKey("id"), "Request with null id should get a response");
        Assertions.assertTrue(response.getString("result").startsWith("Hello ["));
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

    private String sendAndWaitForNoResponse(String message) throws Exception {
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
            return queue.poll(2, TimeUnit.SECONDS);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
