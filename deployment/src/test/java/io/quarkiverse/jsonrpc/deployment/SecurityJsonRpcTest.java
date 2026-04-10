package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
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
 * Tests security annotations on @JsonRPCApi classes.
 * <p>
 * The {@link SecuredResource} is annotated with {@code @RolesAllowed("admin")} at the class level,
 * individual methods override with {@code @PermitAll} or {@code @RolesAllowed("user")}.
 */
public class SecurityJsonRpcTest {

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

    // --- Admin role tests ---

    @Test
    public void testAdminCanAccessAdminMethod() throws Exception {
        String result = callWithAuth("SecuredResource#adminOnly", "admin", "admin");
        Assertions.assertEquals("admin-secret", result);
    }

    @Test
    public void testAdminCanAccessAdminMethodWithParams() throws Exception {
        String result = callWithAuth("SecuredResource#adminData", "admin", "admin", Map.of("key", "test"));
        Assertions.assertEquals("admin:test", result);
    }

    @Test
    public void testUserCannotAccessAdminMethod() throws Exception {
        JsonObject response = callWithAuthRaw("SecuredResource#adminOnly", "user", "user");
        Assertions.assertNotNull(response.getJsonObject("error"), "Expected error response for unauthorized user");
        int code = response.getJsonObject("error").getInteger("code");
        Assertions.assertEquals(-32001, code, "Expected FORBIDDEN error code");
    }

    // --- @PermitAll override tests ---

    @Test
    public void testAdminCanAccessPermitAllMethod() throws Exception {
        String result = callWithAuth("SecuredResource#publicInfo", "admin", "admin");
        Assertions.assertEquals("public-info", result);
    }

    @Test
    public void testUserCanAccessPermitAllMethod() throws Exception {
        String result = callWithAuth("SecuredResource#publicInfo", "user", "user");
        Assertions.assertEquals("public-info", result);
    }

    // --- @RolesAllowed("user") method-level override tests ---

    @Test
    public void testUserCanAccessUserMethod() throws Exception {
        String result = callWithAuth("SecuredResource#userInfo", "user", "user");
        Assertions.assertEquals("user-info", result);
    }

    @Test
    public void testAdminCannotAccessUserMethod() throws Exception {
        JsonObject response = callWithAuthRaw("SecuredResource#userInfo", "admin", "admin");
        Assertions.assertNotNull(response.getJsonObject("error"), "Expected error response for admin on user-only method");
        int code = response.getJsonObject("error").getInteger("code");
        Assertions.assertEquals(-32001, code, "Expected FORBIDDEN error code");
    }

    // --- Helper methods ---

    private String callWithAuth(String method, String username, String password) throws Exception {
        return callWithAuth(method, username, password, Map.of());
    }

    private String callWithAuth(String method, String username, String password, Map<String, Object> params)
            throws Exception {
        JsonObject response = callWithAuthRaw(method, username, password, params);
        String error = response.getString("error");
        if (error != null) {
            Assertions.fail("Unexpected error: " + error);
        }
        return response.getString("result");
    }

    private JsonObject callWithAuthRaw(String method, String username, String password) throws Exception {
        return callWithAuthRaw(method, username, password, Map.of());
    }

    private JsonObject callWithAuthRaw(String method, String username, String password, Map<String, Object> params)
            throws Exception {
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
                            if (params != null && !params.isEmpty()) {
                                jsonObject.put("params", JsonObject.mapFrom(params));
                            }
                            ws.writeTextMessage(jsonObject.encodePrettily());
                        } else {
                            // Connection rejected (e.g., 401/403 during upgrade)
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
