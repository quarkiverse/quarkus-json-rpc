package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;

public class NormalJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class);
            });

    @Test
    public void testHello() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello");
        Assertions.assertTrue(result.startsWith("Hello [executor-thread-"), result);
    }

    @Test
    public void testHelloNonBlocking() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloNonBlocking");
        Assertions.assertTrue(result.startsWith("Hello [vert.x-eventloop-thread-"), result);
    }

    @Test
    public void testHelloWithOneParam() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello", Map.of("name", "Koos"));
        Assertions.assertTrue(result.startsWith("Hello Koos [executor-thread-"));
    }

    @Test
    public void testHelloWithOneParamPosition() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello", new String[] { "Mienkie" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie [executor-thread-"));
    }

    @Test
    public void testHelloNonBlockingWithOneParam() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloNonBlocking", Map.of("name", "Piet"));
        Assertions.assertTrue(result.startsWith("Hello Piet [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloNonBlockingWithOneParamPosition() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloNonBlocking", new String[] { "Christina" });
        Assertions.assertTrue(result.startsWith("Hello Christina [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloWithTwoParams() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello", Map.of("name", "Koos", "surname", "van der Merwe"));
        Assertions.assertTrue(result.startsWith("Hello Koos van der Merwe [executor-thread-"));
    }

    @Test
    public void testHelloWithTwoParamsPosition() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello", new String[] { "Mienkie", "van der Westhuizen" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie van der Westhuizen [executor-thread-"));
    }

    @Test
    public void testHelloNonBlockingWithTwoParams() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloNonBlocking", Map.of("name", "Piet", "surname", "Pompies"));
        Assertions.assertTrue(result.startsWith("Hello Piet Pompies [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloNonBlockingWithTwoParamsPosition() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloNonBlocking", new String[] { "Christina", "Storm" });
        Assertions.assertTrue(result.startsWith("Hello Christina Storm [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUni() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUni");
        Assertions.assertTrue(result.startsWith("Hello [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniBlocking() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUniBlocking");
        Assertions.assertTrue(result.startsWith("Hello [executor-thread-"));
    }

    @Test
    public void testHelloUniWithOneParam() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUni", Map.of("name", "Koos"));
        Assertions.assertTrue(result.startsWith("Hello Koos [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniWithOneParamPosition() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUni", new String[] { "Mienkie" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniBlockingWithOneParam() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUniBlocking", Map.of("name", "Piet"));
        Assertions.assertTrue(result.startsWith("Hello Piet [executor-thread-"));
    }

    @Test
    public void testHelloUniBlockingWithOneParamPosition() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUniBlocking", new String[] { "Christina" });
        Assertions.assertTrue(result.startsWith("Hello Christina [executor-thread-"));
    }

    @Test
    public void testHelloUniWithTwoParams() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUni", Map.of("name", "Koos", "surname", "van der Merwe"));
        Assertions.assertTrue(result.startsWith("Hello Koos van der Merwe [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniWithTwoParamsPosition() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUni", new String[] { "Mienkie", "van der Westhuizen" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie van der Westhuizen [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniBlockingWithTwoParams() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUniBlocking", Map.of("name", "Piet", "surname", "Pompies"));
        Assertions.assertTrue(result.startsWith("Hello Piet Pompies [executor-thread-"));
    }

    @Test
    public void testHelloUniBlockingWithTwoParamsPosition() throws Exception {
        String result = getJsonRpcResponse("HelloResource#helloUniBlocking", new String[] { "Christina", "Storm" });
        Assertions.assertTrue(result.startsWith("Hello Christina Storm [executor-thread-"));
    }

}
