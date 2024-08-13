package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.Pojo;
import io.quarkiverse.jsonrpc.app.Pojo2;
import io.quarkiverse.jsonrpc.app.PojoResource;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class PojoJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(PojoResource.class, Pojo.class, Pojo2.class);
            });

    @Test
    public void testPojo() throws Exception {
        JsonObject result = getJsonRpcResponse("PojoResource#pojo", JsonObject.class);
        assertResult(result, BLOCKING_THREAD);
    }

    @Test
    public void testPojoUni() throws Exception {
        JsonObject result = getJsonRpcResponse("PojoResource#pojoUni", JsonObject.class);
        assertResult(result, NON_BLOCKING_THREAD);
    }

    @Test
    public void testPojoWithOneParam() throws Exception {
        JsonObject result = getJsonRpcResponse("PojoResource#pojo", JsonObject.class, Map.of("name", "Koos"));
        assertResult(result, BLOCKING_THREAD);
    }

    @Test
    public void testPojoWithOneParamUni() throws Exception {
        JsonObject result = getJsonRpcResponse("PojoResource#pojoUni", JsonObject.class, Map.of("name", "Koos"));
        assertResult(result, NON_BLOCKING_THREAD);
    }

    @Test
    public void testPojoWithTwoParam() throws Exception {
        JsonObject result = getJsonRpcResponse("PojoResource#pojo", JsonObject.class,
                Map.of("name", "Koos", "surname", "van der Merwe"));
        assertResult(result, BLOCKING_THREAD);
    }

    @Test
    public void testPojoWithTwoParamUni() throws Exception {
        JsonObject result = getJsonRpcResponse("PojoResource#pojoUni", JsonObject.class,
                Map.of("name", "Koos", "surname", "van der Merwe"));
        assertResult(result, NON_BLOCKING_THREAD);
    }

    @Test
    public void testPojoInput() throws Exception {
        JsonObject result = getJsonRpcResponse("PojoResource#parrot", JsonObject.class, new Object[] { createInput() });
        assertResult(result, BLOCKING_THREAD);
    }

    private void assertResult(JsonObject result, String threadName) {
        String name = result.getString("name");
        String surname = result.getString("surname");
        String thread = result.getString("thread");
        JsonObject pojo2 = result.getJsonObject("pojo2");
        int id = pojo2.getInteger("id");

        Assertions.assertEquals("Koos", name, result.encode());
        Assertions.assertEquals("van der Merwe", surname, result.encode());
        Assertions.assertTrue(thread.startsWith(threadName), result.encode());
        Assertions.assertEquals(id, 0, result.encode());
    }

    private JsonObject createInput() {
        return JsonObject.of(
                "name", "Koos",
                "surname", "van der Merwe",
                "thread", "executor-thread-3",
                "pojo2", JsonObject.of("id", 0));
    }

    private static final String BLOCKING_THREAD = "executor-thread-";
    private static final String NON_BLOCKING_THREAD = "vert.x-eventloop-thread-";

}
