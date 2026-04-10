package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * Tests that standard Quarkus HTTP auth policies ({@code quarkus.http.auth.permission.*})
 * can be used to secure the WebSocket upgrade for the JSON-RPC endpoint.
 * <p>
 * No security annotations are used on the {@link HelloResource} itself — security is
 * applied purely via configuration on the endpoint path.
 */
public class SecurityHttpPolicyJsonRpcTest {

    private final AtomicInteger count = new AtomicInteger(0);

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class);
                root.addAsResource(new org.jboss.shrinkwrap.api.asset.StringAsset(
                        "quarkus.http.auth.basic=true\n" +
                                "quarkus.http.auth.permission.json-rpc.paths=/quarkus/json-rpc\n" +
                                "quarkus.http.auth.permission.json-rpc.policy=authenticated\n" +
                                "quarkus.security.users.embedded.enabled=true\n" +
                                "quarkus.security.users.embedded.plain-text=true\n" +
                                "quarkus.security.users.embedded.users.admin=admin\n" +
                                "quarkus.security.users.embedded.roles.admin=admin\n"),
                        "application.properties");
            });

    @Inject
    Vertx vertx;

    @TestHTTPResource("quarkus/json-rpc")
    URI jsonRpcUri;

    @Test
    public void testAuthenticatedUserCanConnect() throws Exception {
        JsonObject response = callWithAuth("HelloResource#hello", "admin", "admin");
        Assertions.assertNull(response.getJsonObject("error"), "Expected successful response");
        String result = response.getString("result");
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.startsWith("Hello ["), result);
    }

    @Test
    public void testUnauthenticatedUserIsRejected() throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> message = new LinkedBlockingDeque<>();

            // Connect WITHOUT credentials
            WebSocketConnectOptions options = new WebSocketConnectOptions()
                    .setPort(jsonRpcUri.getPort())
                    .setHost(jsonRpcUri.getHost())
                    .setURI(jsonRpcUri.getPath());

            client.connect(options)
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            // Should not succeed — security should reject
                            message.add("UNEXPECTED_SUCCESS");
                        } else {
                            // Expected: connection rejected
                            message.add("REJECTED:" + r.cause().getMessage());
                        }
                    });

            String result = message.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(result, "No response received within timeout");
            Assertions.assertTrue(result.startsWith("REJECTED:"),
                    "Expected WebSocket upgrade to be rejected without credentials, got: " + result);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    private JsonObject callWithAuth(String method, String username, String password) throws Exception {
        int id = count.incrementAndGet();
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> message = new LinkedBlockingDeque<>();

            String credentials = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

            WebSocketConnectOptions options = new WebSocketConnectOptions()
                    .setPort(jsonRpcUri.getPort())
                    .setHost(jsonRpcUri.getHost())
                    .setURI(jsonRpcUri.getPath())
                    .addHeader("Authorization", "Basic " + credentials);

            client.connect(options)
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            WebSocket ws = r.result();
                            ws.textMessageHandler(msg -> message.add(msg));

                            JsonObject jsonObject = JsonObject.of("jsonrpc", "2.0", "id", id, "method", method);
                            ws.writeTextMessage(jsonObject.encodePrettily());
                        } else {
                            message.add("{\"id\":" + id
                                    + ",\"error\":{\"code\":-32000,\"message\":\"" + r.cause().getMessage() + "\"}}");
                        }
                    });

            String response = message.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response, "No response received within timeout");
            return Json.decodeValue(response, JsonObject.class);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
