package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.VoidResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;

public class VoidJsonRpcTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(VoidResource.class));

    @Inject
    Vertx vertx;

    @TestHTTPResource("json-rpc")
    URI jsonRpcUri;

    @BeforeEach
    public void reset() {
        VoidResource.resetLastMessage();
    }

    @Test
    public void testVoidMethodWithId() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "VoidResource#fireAndForget");

        String rawResponse = sendRaw(request.encode());
        JsonObject response = new JsonObject(rawResponse);

        Assertions.assertEquals(1, response.getInteger("id"));
        Assertions.assertTrue(response.containsKey("result"), "Void method should return a result field");
        Assertions.assertNull(response.getValue("result"), "Void method result should be null");
        Assertions.assertEquals("fired", VoidResource.getLastMessage());
    }

    @Test
    public void testVoidMethodWithParamsAndId() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "VoidResource#fireAndForget",
                "params", JsonObject.of("message", "hello"));

        String rawResponse = sendRaw(request.encode());
        JsonObject response = new JsonObject(rawResponse);

        Assertions.assertEquals(2, response.getInteger("id"));
        Assertions.assertTrue(response.containsKey("result"));
        Assertions.assertNull(response.getValue("result"));
        Assertions.assertEquals("hello", VoidResource.getLastMessage());
    }

    @Test
    public void testVoidMethodAsNotification() throws Exception {
        JsonObject notification = JsonObject.of("jsonrpc", "2.0", "method", "VoidResource#fireAndForget");

        String response = sendAndWaitForNoResponse(notification.encode());

        Assertions.assertNull(response, "Server must not reply to a void notification");
        Assertions.assertEquals("fired", VoidResource.getLastMessage());
    }

    @Test
    public void testVoidMethodNonBlocking() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 3, "method",
                "VoidResource#fireAndForgetNonBlocking");

        String rawResponse = sendRaw(request.encode());
        JsonObject response = new JsonObject(rawResponse);

        Assertions.assertEquals(3, response.getInteger("id"));
        Assertions.assertNull(response.getValue("result"));
        Assertions.assertEquals("fired-nb", VoidResource.getLastMessage());
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
