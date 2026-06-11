package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;

public class MetricsJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(HelloResource.class));

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
    public void testConnectionGauge() throws Exception {
        callJsonRpc("HelloResource#hello", 3);

        Assertions.assertNotNull(
                registry.find("jsonrpc.active.connections").gauge(),
                "Expected jsonrpc.active.connections gauge to be registered");
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
}
