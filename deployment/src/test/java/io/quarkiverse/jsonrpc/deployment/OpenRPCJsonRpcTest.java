package io.quarkiverse.jsonrpc.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.AnnotatedPojo;
import io.quarkiverse.jsonrpc.app.BasePojo;
import io.quarkiverse.jsonrpc.app.ChildPojo;
import io.quarkiverse.jsonrpc.app.CollectionResource;
import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkiverse.jsonrpc.app.Pojo;
import io.quarkiverse.jsonrpc.app.Pojo2;
import io.quarkiverse.jsonrpc.app.PojoResource;
import io.quarkiverse.jsonrpc.app.VoidResource;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OpenRPCJsonRpcTest extends JsClientTestBase {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, PojoResource.class,
                        Pojo.class, Pojo2.class, BasePojo.class, ChildPojo.class,
                        AnnotatedPojo.class, VoidResource.class, CollectionResource.class);
            });

    @Test
    public void testOpenRPCDocumentIsServed() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);
        assertEquals("1.3.2", doc.getString("openrpc"));
        assertNotNull(doc.getJsonObject("info"));
        assertEquals("JSON-RPC API", doc.getJsonObject("info").getString("title"));
        assertNotNull(doc.getJsonArray("methods"));
    }

    @Test
    public void testOpenRPCContainsMethods() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);
        JsonArray methods = doc.getJsonArray("methods");
        assertTrue(methods.size() > 0, "Should contain at least one method");

        boolean found = methods.stream()
                .map(o -> ((JsonObject) o).getString("name"))
                .anyMatch(name -> name.startsWith("HelloResource#hello"));
        assertTrue(found, "Should contain a HelloResource#hello method");
    }

    @Test
    public void testMethodWithParamsHasParamSchemas() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "HelloResource#hello(name)");
        assertNotNull(method, "Should find HelloResource#hello(name)");

        JsonArray params = method.getJsonArray("params");
        assertEquals(1, params.size());
        JsonObject param = params.getJsonObject(0);
        assertEquals("name", param.getString("name"));
        assertTrue(param.getBoolean("required"));
        assertEquals("string", param.getJsonObject("schema").getString("type"));
    }

    @Test
    public void testMethodWithNoParamsHasEmptyParams() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "HelloResource#hello");
        assertNotNull(method, "Should find HelloResource#hello (no-arg)");

        JsonArray params = method.getJsonArray("params");
        assertEquals(0, params.size());
    }

    @Test
    public void testUniMethodUnwrapsReturnType() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "HelloResource#helloUni");
        assertNotNull(method, "Should find HelloResource#helloUni");

        JsonObject result = method.getJsonObject("result");
        assertNotNull(result);
        assertEquals("string", result.getJsonObject("schema").getString("type"));
    }

    @Test
    public void testMultiMethodHasSubscriptionTag() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "HelloResource#helloMulti");
        assertNotNull(method, "Should find HelloResource#helloMulti");

        JsonArray tags = method.getJsonArray("tags");
        assertNotNull(tags, "Streaming method should have tags");
        boolean hasSubscription = tags.stream()
                .map(o -> ((JsonObject) o).getString("name"))
                .anyMatch("subscription"::equals);
        assertTrue(hasSubscription, "Streaming method should have 'subscription' tag");

        // Result schema should be the unwrapped type (String, not Multi<String>)
        JsonObject result = method.getJsonObject("result");
        assertNotNull(result);
        assertEquals("string", result.getJsonObject("schema").getString("type"));
    }

    @Test
    public void testPojoReturnUsesRef() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "PojoResource#pojo");
        assertNotNull(method, "Should find PojoResource#pojo");

        JsonObject result = method.getJsonObject("result");
        assertNotNull(result);
        String ref = result.getJsonObject("schema").getString("$ref");
        assertEquals("#/components/schemas/io.quarkiverse.jsonrpc.app.Pojo", ref);
    }

    @Test
    public void testComponentsSchemasContainsPojo() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject schemas = doc.getJsonObject("components").getJsonObject("schemas");
        assertNotNull(schemas);

        JsonObject pojoSchema = schemas.getJsonObject("io.quarkiverse.jsonrpc.app.Pojo");
        assertNotNull(pojoSchema, "Should contain Pojo schema");
        assertEquals("object", pojoSchema.getString("type"));

        JsonObject props = pojoSchema.getJsonObject("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("surname"));
        assertTrue(props.containsKey("pojo2"));

        // Nested Pojo2 should also be in schemas
        JsonObject pojo2Schema = schemas.getJsonObject("io.quarkiverse.jsonrpc.app.Pojo2");
        assertNotNull(pojo2Schema, "Should contain Pojo2 schema");
    }

    @Test
    public void testListReturnTypeIsArray() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "CollectionResource#listOfStrings");
        assertNotNull(method, "Should find CollectionResource#listOfStrings");

        JsonObject schema = method.getJsonObject("result").getJsonObject("schema");
        assertEquals("array", schema.getString("type"));
        assertEquals("string", schema.getJsonObject("items").getString("type"));
    }

    @Test
    public void testMapReturnTypeHasAdditionalProperties() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "CollectionResource#mapOfStrings");
        assertNotNull(method, "Should find CollectionResource#mapOfStrings");

        JsonObject schema = method.getJsonObject("result").getJsonObject("schema");
        assertEquals("object", schema.getString("type"));
        assertEquals("string", schema.getJsonObject("additionalProperties").getString("type"));
    }

    @Test
    public void testInheritedPropertiesIncluded() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject schemas = doc.getJsonObject("components").getJsonObject("schemas");
        JsonObject childSchema = schemas.getJsonObject("io.quarkiverse.jsonrpc.app.ChildPojo");
        assertNotNull(childSchema, "Should contain ChildPojo schema");

        JsonObject props = childSchema.getJsonObject("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("childField"), "Should contain own property");
        assertTrue(props.containsKey("baseField"), "Should contain inherited property from BasePojo");
    }

    @Test
    public void testSchemaUsesFullyQualifiedNamesByDefault() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject schemas = doc.getJsonObject("components").getJsonObject("schemas");
        assertNotNull(schemas);
        assertTrue(schemas.containsKey("io.quarkiverse.jsonrpc.app.Pojo"),
                "Schema keys should use fully-qualified names by default");
        assertFalse(schemas.containsKey("Pojo"),
                "Schema keys should not use simple names by default");
    }

    @Test
    public void testIgnoredMethodIsNotIncluded() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "HelloResource#ignoredMethod");
        assertNull(method, "@JsonRPCIgnore methods should not appear in OpenRPC");
    }

    @Test
    public void testJsonPropertyRenamesSchemaField() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject schemas = doc.getJsonObject("components").getJsonObject("schemas");
        JsonObject annotatedSchema = schemas.getJsonObject("io.quarkiverse.jsonrpc.app.AnnotatedPojo");
        assertNotNull(annotatedSchema, "Should contain AnnotatedPojo schema");

        JsonObject props = annotatedSchema.getJsonObject("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("display_name"),
                "@JsonProperty(\"display_name\") should rename 'name' to 'display_name'");
        assertFalse(props.containsKey("name"),
                "Original field name 'name' should not appear when @JsonProperty renames it");
    }

    @Test
    public void testOptionalParamIsNotRequired() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject method = findMethod(doc, "CollectionResource#withDefault(name|title)");
        assertNotNull(method, "Should find CollectionResource#withDefault(name|title)");

        JsonArray params = method.getJsonArray("params");
        assertEquals(2, params.size());

        JsonObject nameParam = params.getJsonObject(0);
        assertEquals("name", nameParam.getString("name"));
        assertTrue(nameParam.getBoolean("required"), "String param should be required");

        JsonObject titleParam = params.getJsonObject(1);
        assertEquals("title", titleParam.getString("name"));
        assertFalse(titleParam.getBoolean("required"), "Optional<String> param should not be required");
        assertEquals("string", titleParam.getJsonObject("schema").getString("type"),
                "Optional<String> schema should unwrap to string");
    }

    @Test
    public void testJsonIgnoreExcludesSchemaField() throws Exception {
        String body = httpGet("/json-rpc/openrpc.json");
        JsonObject doc = new JsonObject(body);

        JsonObject schemas = doc.getJsonObject("components").getJsonObject("schemas");
        JsonObject annotatedSchema = schemas.getJsonObject("io.quarkiverse.jsonrpc.app.AnnotatedPojo");
        assertNotNull(annotatedSchema, "Should contain AnnotatedPojo schema");

        JsonObject props = annotatedSchema.getJsonObject("properties");
        assertNotNull(props);
        assertFalse(props.containsKey("secret"),
                "@JsonIgnore field should not appear in schema");
        assertTrue(props.containsKey("description"),
                "Non-ignored field should still appear in schema");
    }

    private JsonObject findMethod(JsonObject doc, String methodName) {
        JsonArray methods = doc.getJsonArray("methods");
        for (int i = 0; i < methods.size(); i++) {
            JsonObject method = methods.getJsonObject(i);
            if (methodName.equals(method.getString("name"))) {
                return method;
            }
        }
        return null;
    }
}
