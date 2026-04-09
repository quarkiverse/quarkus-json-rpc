package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.CompletionStageResource;
import io.quarkus.test.QuarkusUnitTest;

public class CompletionStageJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(CompletionStageResource.class);
            });

    @Test
    public void testCompletionStage() throws Exception {
        String result = getJsonRpcResponse("CompletionStageResource#greeting");
        Assertions.assertTrue(result.startsWith("Hello from CompletionStage ["), result);
    }

    @Test
    public void testCompletionStageWithParam() throws Exception {
        String result = getJsonRpcResponse("CompletionStageResource#greeting", Map.of("name", "Koos"));
        Assertions.assertTrue(result.startsWith("Hello Koos from CompletionStage ["), result);
    }

    @Test
    public void testCompletionStageWithParamPosition() throws Exception {
        String result = getJsonRpcResponse("CompletionStageResource#greeting", new String[] { "Mienkie" });
        Assertions.assertTrue(result.startsWith("Hello Mienkie from CompletionStage ["), result);
    }

    @Test
    public void testCompletionStageBlocking() throws Exception {
        String result = getJsonRpcResponse("CompletionStageResource#greetingBlocking");
        Assertions.assertTrue(result.startsWith("Hello from CompletionStage [executor-thread-"), result);
    }

    @Test
    public void testCompletableFuture() throws Exception {
        String result = getJsonRpcResponse("CompletionStageResource#greetingFuture");
        Assertions.assertTrue(result.startsWith("Hello from CompletableFuture ["), result);
    }

    @Test
    public void testCompletableFutureWithParam() throws Exception {
        String result = getJsonRpcResponse("CompletionStageResource#greetingFuture", Map.of("name", "Piet"));
        Assertions.assertTrue(result.startsWith("Hello Piet from CompletableFuture ["), result);
    }

    @Test
    public void testCompletableFutureWithParamPosition() throws Exception {
        String result = getJsonRpcResponse("CompletionStageResource#greetingFuture", new String[] { "Christina" });
        Assertions.assertTrue(result.startsWith("Hello Christina from CompletableFuture ["), result);
    }

    @Test
    public void testCompletableFutureBlocking() throws Exception {
        String result = getJsonRpcResponse("CompletionStageResource#greetingFutureBlocking");
        Assertions.assertTrue(result.startsWith("Hello from CompletableFuture [executor-thread-"), result);
    }
}
