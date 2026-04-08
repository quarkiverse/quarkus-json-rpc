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

            // Collect all messages (items + ack with null result)
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected message " + i);
                JsonObject json = new JsonObject(msg);
                String result = json.getString("result");
                if (result != null) {
                    items.add(result);
                }
            }
            Assertions.assertEquals(List.of("item-0", "item-1", "item-2"), items);
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

            // Collect all messages (items + ack with null result)
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected message " + i);
                JsonObject json = new JsonObject(msg);
                String result = json.getString("result");
                if (result != null) {
                    items.add(result);
                }
            }
            Assertions.assertEquals(List.of("test-0", "test-1", "test-2"), items);
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

            // Collect messages — expect ack and error in some order
            boolean foundError = false;
            for (int i = 0; i < 2; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected message " + i);
                JsonObject json = new JsonObject(msg);
                JsonObject error = json.getJsonObject("error");
                if (error != null) {
                    foundError = true;
                    Assertions.assertTrue(error.getString("message").contains("Multi test error"),
                            "Expected 'Multi test error' in message but got: " + error.getString("message"));
                }
            }
            Assertions.assertTrue(foundError, "Expected an error response from the failing Multi");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
