package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.CustomPathResource;
import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

public class CustomPathJsonRpcTest extends JsonRpcParent {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, CustomPathResource.class);
            });

    @TestHTTPResource("custom-rpc")
    URI customRpcUri;

    @Test
    public void testDefaultPathStillWorks() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello");
        Assertions.assertTrue(result.startsWith("Hello [executor-thread-"), result);
    }

    @Test
    public void testDefaultPathWithParam() throws Exception {
        String result = getJsonRpcResponse("HelloResource#hello", Map.of("name", "Koos"));
        Assertions.assertTrue(result.startsWith("Hello Koos [executor-thread-"), result);
    }

    @Test
    public void testCustomPathHello() throws Exception {
        String result = getJsonRpcResponse(customRpcUri, "CustomPathResource#customHello");
        Assertions.assertTrue(result.startsWith("Hello from custom path [executor-thread-"), result);
    }

    @Test
    public void testCustomPathHelloWithParam() throws Exception {
        String result = getJsonRpcResponse(customRpcUri, "CustomPathResource#customHello",
                Map.of("name", "Piet"));
        Assertions.assertTrue(result.startsWith("Hello Piet from custom path [executor-thread-"), result);
    }
}
