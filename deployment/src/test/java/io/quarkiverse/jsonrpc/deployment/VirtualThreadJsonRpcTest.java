package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.VirtualThreadResource;
import io.quarkus.test.QuarkusUnitTest;

public class VirtualThreadJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(VirtualThreadResource.class);
            });

    @Test
    public void testHelloVirtualThread() throws Exception {
        String result = getJsonRpcResponse("VirtualThreadResource#hello");
        Assertions.assertTrue(result.startsWith("Hello ["), result);
        if (Runtime.version().feature() >= 21) {
            Assertions.assertTrue(result.startsWith("Hello [quarkus-virtual-thread-"), result);
        }
    }

    @Test
    public void testHelloVirtualThreadWithParam() throws Exception {
        String result = getJsonRpcResponse("VirtualThreadResource#hello", Map.of("name", "Koos"));
        Assertions.assertTrue(result.startsWith("Hello Koos ["), result);
        if (Runtime.version().feature() >= 21) {
            Assertions.assertTrue(result.startsWith("Hello Koos [quarkus-virtual-thread-"), result);
        }
    }

    @Test
    public void testHelloVirtualThreadWithParamPosition() throws Exception {
        String result = getJsonRpcResponse("VirtualThreadResource#hello", new String[] { "Mienkie" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie ["), result);
        if (Runtime.version().feature() >= 21) {
            Assertions.assertTrue(result.startsWith("Hello Mienkie [quarkus-virtual-thread-"), result);
        }
    }

    @Test
    public void testHelloUniVirtualThread() throws Exception {
        String result = getJsonRpcResponse("VirtualThreadResource#helloUni");
        Assertions.assertTrue(result.startsWith("Hello ["), result);
        if (Runtime.version().feature() >= 21) {
            Assertions.assertTrue(result.startsWith("Hello [quarkus-virtual-thread-"), result);
        }
    }

    @Test
    public void testHelloUniVirtualThreadWithParam() throws Exception {
        String result = getJsonRpcResponse("VirtualThreadResource#helloUni", Map.of("name", "Piet"));
        Assertions.assertTrue(result.startsWith("Hello Piet ["), result);
        if (Runtime.version().feature() >= 21) {
            Assertions.assertTrue(result.startsWith("Hello Piet [quarkus-virtual-thread-"), result);
        }
    }

    @Test
    public void testHelloCompletionStageVirtualThread() throws Exception {
        String result = getJsonRpcResponse("VirtualThreadResource#helloCompletionStage");
        Assertions.assertTrue(result.startsWith("Hello ["), result);
        if (Runtime.version().feature() >= 21) {
            Assertions.assertTrue(result.startsWith("Hello [quarkus-virtual-thread-"), result);
        }
    }

    @Test
    public void testHelloCompletionStageVirtualThreadWithParam() throws Exception {
        String result = getJsonRpcResponse("VirtualThreadResource#helloCompletionStage", Map.of("name", "Sannie"));
        Assertions.assertTrue(result.startsWith("Hello Sannie ["), result);
        if (Runtime.version().feature() >= 21) {
            Assertions.assertTrue(result.startsWith("Hello Sannie [quarkus-virtual-thread-"), result);
        }
    }

    @Test
    public void testHelloBlockingVirtualThread() throws Exception {
        String result = getJsonRpcResponse("VirtualThreadResource#helloBlocking");
        Assertions.assertTrue(result.startsWith("Hello ["), result);
        if (Runtime.version().feature() >= 21) {
            Assertions.assertTrue(result.startsWith("Hello [quarkus-virtual-thread-"), result);
        }
    }
}
