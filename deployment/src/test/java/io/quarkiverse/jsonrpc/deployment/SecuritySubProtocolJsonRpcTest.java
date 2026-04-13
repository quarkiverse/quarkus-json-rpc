package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.SecuredResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * Tests authentication via the {@code Sec-WebSocket-Protocol} header encoding pattern.
 * <p>
 * Uses the same {@link SecuredResource} and embedded users as {@link SecurityJsonRpcTest},
 * but encodes credentials in the sub-protocol header instead of the {@code Authorization} header.
 */
public class SecuritySubProtocolJsonRpcTest {

    private final AtomicInteger count = new AtomicInteger(0);

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(SecuredResource.class);
                root.addAsResource(new org.jboss.shrinkwrap.api.asset.StringAsset(
                        "quarkus.http.auth.basic=true\n" +
                                "quarkus.security.users.embedded.enabled=true\n" +
                                "quarkus.security.users.embedded.plain-text=true\n" +
                                "quarkus.security.users.embedded.users.admin=admin\n" +
                                "quarkus.security.users.embedded.roles.admin=admin\n" +
                                "quarkus.security.users.embedded.users.user=user\n" +
                                "quarkus.security.users.embedded.roles.user=user\n"),
                        "application.properties");
            });

    @Inject
    Vertx vertx;

    @TestHTTPResource("quarkus/json-rpc")
    URI jsonRpcUri;

    @Test
    public void testAdminCanAccessViaSubProtocol() throws Exception {
        String result = callWithSubProtocolAuth("SecuredResource#adminOnly", "admin", "admin");
        Assertions.assertEquals("admin-secret", result);
    }

    @Test
    public void testUserForbiddenViaSubProtocol() throws Exception {
        JsonObject response = callWithSubProtocolAuthRaw("SecuredResource#adminOnly", "user", "user");
        Assertions.assertNotNull(response.getJsonObject("error"), "Expected error response for unauthorized user");
        int code = response.getJsonObject("error").getInteger("code");
        Assertions.assertEquals(-32001, code, "Expected FORBIDDEN error code");
    }

    @Test
    public void testAnonymousCannotAccessAdminViaSubProtocol() throws Exception {
        JsonObject response = callAnonymousRaw("SecuredResource#adminOnly");
        Assertions.assertNotNull(response.getJsonObject("error"), "Expected error response for anonymous user");
        int code = response.getJsonObject("error").getInteger("code");
        Assertions.assertTrue(code == -32000 || code == -32001,
                "Expected UNAUTHORIZED or FORBIDDEN error code, got: " + code);
    }

    @Test
    public void testAdminCanAccessPermitAllViaSubProtocol() throws Exception {
        String result = callWithSubProtocolAuth("SecuredResource#publicInfo", "admin", "admin");
        Assertions.assertEquals("public-info", result);
    }

    @Test
    public void testUserCanAccessPermitAllViaSubProtocol() throws Exception {
        String result = callWithSubProtocolAuth("SecuredResource#publicInfo", "user", "user");
        Assertions.assertEquals("public-info", result);
    }

    // --- Helper methods ---

    private String callWithSubProtocolAuth(String method, String username, String password) throws Exception {
        JsonObject response = callWithSubProtocolAuthRaw(method, username, password);
        JsonObject error = response.getJsonObject("error");
        if (error != null) {
            Assertions.fail("Unexpected error: " + error.encodePrettily());
        }
        return response.getString("result");
    }

    private JsonObject callWithSubProtocolAuthRaw(String method, String username, String password) throws Exception {
        int id = count.incrementAndGet();
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> message = new LinkedBlockingDeque<>();

            String credentials = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            String encodedHeader = URLEncoder.encode(
                    "quarkus-http-upgrade#Authorization#Basic " + credentials,
                    StandardCharsets.UTF_8);

            // Use addHeader instead of addSubProtocol to match browser behavior.
            // Browsers send Sec-WebSocket-Protocol as a raw header and accept the
            // connection even when the server doesn't echo back a subprotocol.
            // The Vert.x WebSocket client is stricter and would reject in that case.
            WebSocketConnectOptions options = new WebSocketConnectOptions()
                    .setPort(jsonRpcUri.getPort())
                    .setHost(jsonRpcUri.getHost())
                    .setURI(jsonRpcUri.getPath())
                    .addHeader("Sec-WebSocket-Protocol", "bearer-token-carrier, " + encodedHeader);

            client.connect(options)
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            WebSocket ws = r.result();
                            ws.textMessageHandler(msg -> message.add(msg));

                            JsonObject jsonObject = JsonObject.of("jsonrpc", "2.0", "id", id, "method", method);
                            ws.writeTextMessage(jsonObject.encodePrettily());
                        } else {
                            message.add("{\"id\":" + id
                                    + ",\"error\":{\"code\":-32000,\"message\":\"WebSocket upgrade rejected: "
                                    + r.cause().getMessage() + "\"}}");
                        }
                    });

            String response = message.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response, "No response received within timeout");
            return Json.decodeValue(response, JsonObject.class);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    private JsonObject callAnonymousRaw(String method) throws Exception {
        int id = count.incrementAndGet();
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> message = new LinkedBlockingDeque<>();

            WebSocketConnectOptions options = new WebSocketConnectOptions()
                    .setPort(jsonRpcUri.getPort())
                    .setHost(jsonRpcUri.getHost())
                    .setURI(jsonRpcUri.getPath());

            client.connect(options)
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            WebSocket ws = r.result();
                            ws.textMessageHandler(msg -> message.add(msg));

                            JsonObject jsonObject = JsonObject.of("jsonrpc", "2.0", "id", id, "method", method);
                            ws.writeTextMessage(jsonObject.encodePrettily());
                        } else {
                            message.add("{\"id\":" + id
                                    + ",\"error\":{\"code\":-32000,\"message\":\"WebSocket upgrade rejected: "
                                    + r.cause().getMessage() + "\"}}");
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
