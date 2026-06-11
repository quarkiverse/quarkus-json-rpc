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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkiverse.jsonrpc.app.FailingResource;
import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkiverse.jsonrpc.app.MultiResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;

public class MetricsJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(
                    HelloResource.class, FailingResource.class, MultiResource.class));

    @Inject
    Vertx vertx;

    @Inject
    MeterRegistry registry;

    @TestHTTPResource("json-rpc")
    URI jsonRpcUri;

    @Test
    public void testRequestMetricsRecorded() throws Exception {
        callJsonRpc("HelloResource#hello", 1);
        callJsonRpc("HelloResource#hello", 2);

        Timer successTimer = registry.find("jsonrpc.requests")
                .tag("method", "HelloResource#hello")
                .tag("outcome", "success")
                .timer();
        Assertions.assertNotNull(successTimer, "Expected jsonrpc.requests timer to be registered");
        Assertions.assertEquals(2, successTimer.count());
        Assertions.assertTrue(successTimer.totalTime(TimeUnit.NANOSECONDS) > 0);
    }

    @Test
    public void testErrorMetricsRecorded() throws Exception {
        callJsonRpcExpectError("FailingResource#fail", 10);

        Timer errorTimer = registry.find("jsonrpc.requests")
                .tag("method", "FailingResource#fail")
                .tag("outcome", "error")
                .timer();
        Assertions.assertNotNull(errorTimer, "Expected jsonrpc.requests error timer to be registered");
        Assertions.assertEquals(1, errorTimer.count());
    }

    @Test
    public void testConnectionGauge() throws Exception {
        Gauge gauge = registry.find("jsonrpc.active.connections").gauge();
        Assertions.assertNotNull(gauge, "Expected jsonrpc.active.connections gauge to be registered");

        double before = gauge.value();
        CompletableFuture<WebSocket> connected = new CompletableFuture<>();
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            connected.complete(r.result());
                        } else {
                            connected.completeExceptionally(r.cause());
                        }
                    });
            WebSocket ws = connected.get(5, TimeUnit.SECONDS);

            Assertions.assertTrue(gauge.value() > before,
                    "Gauge should increase when a connection is opened");

            CompletableFuture<Void> closed = new CompletableFuture<>();
            ws.close().onComplete(r -> closed.complete(null));
            closed.get(5, TimeUnit.SECONDS);
            Thread.sleep(100);

            Assertions.assertTrue(gauge.value() <= before,
                    "Gauge should decrease when a connection is closed");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testMultiInvocationMetrics() throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 20, "method", "MultiResource#items");

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

            // Ack
            String ackMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(ackMsg, "Expected ack message");
            JsonObject ack = new JsonObject(ackMsg);
            Assertions.assertNotNull(ack.getString("result"), "Ack should contain subscription ID");

            // Drain items + completion
            List<String> received = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String msg = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(msg, "Expected message " + i);
                received.add(msg);
            }

            Timer successTimer = registry.find("jsonrpc.requests")
                    .tag("method", "MultiResource#items")
                    .tag("outcome", "success")
                    .timer();
            Assertions.assertNotNull(successTimer,
                    "Expected jsonrpc.requests success timer for Multi method");
            Assertions.assertTrue(successTimer.count() > 0);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testMultiSubscriptionErrorMetrics() throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            CompletableFuture<Void> connected = new CompletableFuture<>();
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 30, "method", "MultiResource#failing");

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

            // Ack
            String ackMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(ackMsg, "Expected ack message");

            // Error notification
            String errMsg = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(errMsg, "Expected error notification");
            JsonObject errJson = new JsonObject(errMsg);
            Assertions.assertNotNull(errJson.getJsonObject("params").getJsonObject("error"),
                    "Expected error in notification params");

            Counter errorCounter = registry.find("jsonrpc.subscription.errors")
                    .tag("method", "MultiResource#failing")
                    .counter();
            Assertions.assertNotNull(errorCounter,
                    "Expected jsonrpc.subscription.errors counter for failing Multi");
            Assertions.assertTrue(errorCounter.count() > 0);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void testSubscriptionsActiveGauge() throws Exception {
        Gauge gauge = registry.find("jsonrpc.subscriptions.active").gauge();
        Assertions.assertNotNull(gauge, "Expected jsonrpc.subscriptions.active gauge to be registered");
    }

    private void callJsonRpc(String method, int id) throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
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
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    private void callJsonRpcExpectError(String method, int id) throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
            client.connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
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
            Assertions.assertNotNull(json.getJsonObject("error"), "Expected an error response");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
