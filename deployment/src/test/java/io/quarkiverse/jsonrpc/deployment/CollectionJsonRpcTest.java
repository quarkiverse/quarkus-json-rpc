package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.CollectionResource;
import io.quarkiverse.jsonrpc.app.Pojo;
import io.quarkiverse.jsonrpc.app.Pojo2;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CollectionJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(CollectionResource.class, Pojo.class, Pojo2.class);
            });

    // --- Return type tests ---

    @Test
    public void testListOfStrings() throws Exception {
        JsonArray result = getJsonRpcResponse("CollectionResource#listOfStrings", JsonArray.class);
        Assertions.assertEquals(3, result.size());
        Assertions.assertTrue(result.contains("alpha"));
        Assertions.assertTrue(result.contains("beta"));
        Assertions.assertTrue(result.contains("gamma"));
    }

    @Test
    public void testListOfPojos() throws Exception {
        JsonArray result = getJsonRpcResponse("CollectionResource#listOfPojos", JsonArray.class);
        Assertions.assertEquals(2, result.size());
        JsonObject first = result.getJsonObject(0);
        Assertions.assertNotNull(first.getString("name"));
    }

    @Test
    public void testMapOfStrings() throws Exception {
        JsonObject result = getJsonRpcResponse("CollectionResource#mapOfStrings", JsonObject.class);
        Assertions.assertEquals("value1", result.getString("key1"));
        Assertions.assertEquals("value2", result.getString("key2"));
    }

    @Test
    public void testMapOfPojos() throws Exception {
        JsonObject result = getJsonRpcResponse("CollectionResource#mapOfPojos", JsonObject.class);
        JsonObject alice = result.getJsonObject("alice");
        Assertions.assertEquals("Alice", alice.getString("name"));
        JsonObject bob = result.getJsonObject("bob");
        Assertions.assertEquals("Bob", bob.getString("name"));
    }

    @Test
    public void testSetOfStrings() throws Exception {
        JsonArray result = getJsonRpcResponse("CollectionResource#setOfStrings", JsonArray.class);
        Assertions.assertEquals(3, result.size());
    }

    @Test
    public void testOptionalPresent() throws Exception {
        String result = getJsonRpcResponse("CollectionResource#optionalPresent");
        Assertions.assertEquals("present-value", result);
    }

    @Test
    public void testOptionalEmpty() throws Exception {
        String result = getJsonRpcResponse("CollectionResource#optionalEmpty");
        Assertions.assertNull(result);
    }

    @Test
    public void testArrayOfStrings() throws Exception {
        JsonArray result = getJsonRpcResponse("CollectionResource#arrayOfStrings", JsonArray.class);
        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals("x", result.getString(0));
        Assertions.assertEquals("y", result.getString(1));
        Assertions.assertEquals("z", result.getString(2));
    }

    // --- Uni-wrapped return type tests ---

    @Test
    public void testUniListOfStrings() throws Exception {
        JsonArray result = getJsonRpcResponse("CollectionResource#uniListOfStrings", JsonArray.class);
        Assertions.assertEquals(3, result.size());
        Assertions.assertTrue(result.contains("alpha"));
    }

    @Test
    public void testUniMapOfPojos() throws Exception {
        JsonObject result = getJsonRpcResponse("CollectionResource#uniMapOfPojos", JsonObject.class);
        JsonObject alice = result.getJsonObject("alice");
        Assertions.assertEquals("Alice", alice.getString("name"));
    }

    // --- Parameter deserialization tests (named params) ---

    @Test
    public void testListOfStringsParam() throws Exception {
        JsonArray items = new JsonArray().add("a").add("b").add("c");
        String result = getJsonRpcResponse("CollectionResource#joinStrings",
                String.class, Map.of("items", items));
        Assertions.assertEquals("a,b,c", result);
    }

    @Test
    public void testListOfStringsParamPositioned() throws Exception {
        JsonArray items = new JsonArray().add("x").add("y");
        String result = getJsonRpcResponse("CollectionResource#joinStrings",
                String.class, new Object[] { items });
        Assertions.assertEquals("x,y", result);
    }

    @Test
    public void testListOfPojosParam() throws Exception {
        JsonArray pojos = new JsonArray()
                .add(createPojoJson("Alice"))
                .add(createPojoJson("Bob"));
        Integer result = getJsonRpcResponse("CollectionResource#countPojos",
                Integer.class, Map.of("pojos", pojos));
        Assertions.assertEquals(2, result);
    }

    @Test
    public void testMapOfStringsParam() throws Exception {
        JsonObject data = JsonObject.of("foo", "bar", "baz", "qux");
        String result = getJsonRpcResponse("CollectionResource#lookupInMap",
                String.class, Map.of("data", data, "key", "foo"));
        Assertions.assertEquals("bar", result);
    }

    @Test
    public void testMapOfPojosParam() throws Exception {
        JsonObject data = JsonObject.of("alice", createPojoJson("Alice"));
        String result = getJsonRpcResponse("CollectionResource#lookupPojoName",
                String.class, Map.of("data", data, "key", "alice"));
        Assertions.assertEquals("Alice", result);
    }

    @Test
    public void testArrayParam() throws Exception {
        JsonArray items = new JsonArray().add("a").add("b").add("c");
        Integer result = getJsonRpcResponse("CollectionResource#arrayLength",
                Integer.class, Map.of("items", items));
        Assertions.assertEquals(3, result);
    }

    private JsonObject createPojoJson(String name) {
        return JsonObject.of(
                "name", name,
                "surname", "Test",
                "thread", "test-thread",
                "pojo2", JsonObject.of("id", 0, "desc", "desc", "ref", UUID.randomUUID().toString()));
    }
}
