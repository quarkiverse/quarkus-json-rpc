package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.ScopedHelloResource;
import io.quarkus.test.QuarkusUnitTest;

public class ScopedJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(ScopedHelloResource.class);
            });

    @Test
    public void testHello() throws Exception {
        String result = getJsonRpcResponse("scoped#hello");
        Assertions.assertTrue(result.startsWith("Hello scoped [executor-thread-"), result);
    }

    @Test
    public void testHelloNonBlocking() throws Exception {
        String result = getJsonRpcResponse("scoped#helloNonBlocking");
        Assertions.assertTrue(result.startsWith("Hello scoped [vert.x-eventloop-thread-"), result);
    }

    @Test
    public void testHelloWithOneParam() throws Exception {
        String result = getJsonRpcResponse("scoped#hello", Map.of("name", "Koos"));
        Assertions.assertTrue(result.startsWith("Hello Koos scoped [executor-thread-"));
    }

    @Test
    public void testHelloWithOneParamPosition() throws Exception {
        String result = getJsonRpcResponse("scoped#hello", new String[] { "Mienkie" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie scoped [executor-thread-"));
    }

    @Test
    public void testHelloNonBlockingWithOneParam() throws Exception {
        String result = getJsonRpcResponse("scoped#helloNonBlocking", Map.of("name", "Piet"));
        Assertions.assertTrue(result.startsWith("Hello Piet scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloNonBlockingWithOneParamPosition() throws Exception {
        String result = getJsonRpcResponse("scoped#helloNonBlocking", new String[] { "Christina" });
        Assertions.assertTrue(result.startsWith("Hello Christina scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloWithTwoParams() throws Exception {
        String result = getJsonRpcResponse("scoped#hello", Map.of("name", "Koos", "surname", "van der Merwe"));
        Assertions.assertTrue(result.startsWith("Hello Koos van der Merwe scoped [executor-thread-"));
    }

    @Test
    public void testHelloWithTwoParamsPosition() throws Exception {
        String result = getJsonRpcResponse("scoped#hello", new String[] { "Mienkie", "van der Westhuizen" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie van der Westhuizen scoped [executor-thread-"));
    }

    @Test
    public void testHelloNonBlockingWithTwoParams() throws Exception {
        String result = getJsonRpcResponse("scoped#helloNonBlocking", Map.of("name", "Piet", "surname", "Pompies"));
        Assertions.assertTrue(result.startsWith("Hello Piet Pompies scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloNonBlockingWithTwoParamsPosition() throws Exception {
        String result = getJsonRpcResponse("scoped#helloNonBlocking", new String[] { "Christina", "Storm" });
        Assertions.assertTrue(result.startsWith("Hello Christina Storm scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUni() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUni");
        Assertions.assertTrue(result.startsWith("Hello scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniBlocking() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUniBlocking");
        Assertions.assertTrue(result.startsWith("Hello scoped [executor-thread-"));
    }

    @Test
    public void testHelloUniWithOneParam() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUni", Map.of("name", "Koos"));
        Assertions.assertTrue(result.startsWith("Hello Koos scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniWithOneParamPosition() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUni", new String[] { "Mienkie" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniBlockingWithOneParam() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUniBlocking", Map.of("name", "Piet"));
        Assertions.assertTrue(result.startsWith("Hello Piet scoped [executor-thread-"));
    }

    @Test
    public void testHelloUniBlockingWithOneParamPosition() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUniBlocking", new String[] { "Christina" });
        Assertions.assertTrue(result.startsWith("Hello Christina scoped [executor-thread-"));
    }

    @Test
    public void testHelloUniWithTwoParams() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUni", Map.of("name", "Koos", "surname", "van der Merwe"));
        Assertions.assertTrue(result.startsWith("Hello Koos van der Merwe scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniWithTwoParamsPosition() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUni", new String[] { "Mienkie", "van der Westhuizen" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie van der Westhuizen scoped [vert.x-eventloop-thread-"));
    }

    @Test
    public void testHelloUniBlockingWithTwoParams() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUniBlocking", Map.of("name", "Piet", "surname", "Pompies"));
        Assertions.assertTrue(result.startsWith("Hello Piet Pompies scoped [executor-thread-"));
    }

    @Test
    public void testHelloUniBlockingWithTwoParamsPosition() throws Exception {
        String result = getJsonRpcResponse("scoped#helloUniBlocking", new String[] { "Christina", "Storm" });
        Assertions.assertTrue(result.startsWith("Hello Christina Storm scoped [executor-thread-"));
    }
}
