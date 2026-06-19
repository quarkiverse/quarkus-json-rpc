package io.quarkiverse.jsonrpc.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.CustomPathResource;
import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CustomPathOpenRPCTest extends JsClientTestBase {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, CustomPathResource.class);
            });

    @Test
    public void testDefaultOpenRPCContainsOnlyDefaultMethods() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);
        assertEquals("1.3.2", doc.getString("openrpc"));

        JsonArray methods = doc.getJsonArray("methods");
        assertNotNull(methods);

        boolean hasHello = methods.stream()
                .map(o -> ((JsonObject) o).getString("name"))
                .anyMatch(name -> name.startsWith("HelloResource#"));
        assertTrue(hasHello, "Default OpenRPC should contain HelloResource methods");

        boolean hasCustom = methods.stream()
                .map(o -> ((JsonObject) o).getString("name"))
                .anyMatch(name -> name.startsWith("CustomPathResource#"));
        assertFalse(hasCustom, "Default OpenRPC should not contain CustomPathResource methods");
    }

    @Test
    public void testCustomPathOpenRPCContainsOnlyCustomMethods() throws Exception {
        String body = httpGet("/custom-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);
        assertEquals("1.3.2", doc.getString("openrpc"));

        JsonArray methods = doc.getJsonArray("methods");
        assertNotNull(methods);
        assertTrue(methods.size() > 0, "Custom path OpenRPC should contain methods");

        boolean hasCustom = methods.stream()
                .map(o -> ((JsonObject) o).getString("name"))
                .anyMatch(name -> name.startsWith("CustomPathResource#"));
        assertTrue(hasCustom, "Custom path OpenRPC should contain CustomPathResource methods");

        boolean hasHello = methods.stream()
                .map(o -> ((JsonObject) o).getString("name"))
                .anyMatch(name -> name.startsWith("HelloResource#"));
        assertFalse(hasHello, "Custom path OpenRPC should not contain HelloResource methods");
    }
}
