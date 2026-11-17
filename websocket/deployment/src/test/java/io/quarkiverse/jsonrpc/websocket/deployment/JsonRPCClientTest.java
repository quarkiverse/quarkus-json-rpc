package io.quarkiverse.jsonrpc.websocket.deployment;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkiverse.jsonrpc.app.MultiResource;
import io.quarkiverse.jsonrpc.app.Pojo;
import io.quarkiverse.jsonrpc.app.Pojo2;
import io.quarkiverse.jsonrpc.app.PojoResource;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys;
import io.quarkiverse.jsonrpc.websocket.client.JsonRPCClient;
import io.quarkiverse.jsonrpc.websocket.client.JsonRPCClientException;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;

public class JsonRPCClientTest {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, MultiResource.class, PojoResource.class, Pojo.class, Pojo2.class);
            });

    @Inject
    Vertx vertx;

    @TestHTTPResource("json-rpc")
    URI jsonRpcUri;

    JsonRPCClient client;

    @BeforeEach
    public void connect() {
        client = JsonRPCClient.connect(vertx, URI.create("ws://" + jsonRpcUri.getHost() + ":" + jsonRpcUri.getPort()
                + jsonRpcUri.getPath()));
    }

    @AfterEach
    public void close() {
        client.close();
    }

    @Test
    public void testCallWithNamedParams() {
        String result = client.call("HelloResource#hello", Map.of("name", "Koos"), String.class);
        Assertions.assertTrue(result.startsWith("Hello Koos "), result);
    }

    @Test
    public void testCallWithPositionalParams() {
        String result = client.call("HelloResource#hello", String.class, "Koos", "van der Merwe");
        Assertions.assertTrue(result.startsWith("Hello Koos van der Merwe "), result);
    }

    @Test
    public void testCallWithoutParams() {
        String result = client.call("HelloResource#hello", String.class);
        Assertions.assertTrue(result.startsWith("Hello ["), result);
    }

    @Test
    public void testCallAsyncReturningPojo() {
        Pojo pojo = client.callAsync("PojoResource#pojoUni", Map.of("name", "Koos"), Pojo.class)
                .await().atMost(Duration.ofSeconds(10));
        Assertions.assertEquals("Koos", pojo.getName());
    }

    @Test
    public void testConcurrentCallsOnOneConnection() {
        List<String> results = List.of("a", "b", "c", "d").stream()
                .map(name -> client.callAsync("HelloResource#hello", String.class, name))
                .toList().stream()
                .map(uni -> uni.await().atMost(Duration.ofSeconds(10)))
                .toList();
        for (int i = 0; i < results.size(); i++) {
            Assertions.assertTrue(results.get(i).startsWith("Hello " + "abcd".charAt(i) + " "), results.get(i));
        }
    }

    @Test
    public void testError() {
        JsonRPCClientException e = Assertions.assertThrows(JsonRPCClientException.class,
                () -> client.call("HelloResource#doesNotExist", String.class));
        Assertions.assertEquals(JsonRPCKeys.METHOD_NOT_FOUND, e.getCode());
    }

    @Test
    public void testSubscribe() {
        List<String> items = client.subscribe("MultiResource#items", String.class, "x")
                .collect().asList().await().atMost(Duration.ofSeconds(10));
        Assertions.assertEquals(List.of("x-0", "x-1", "x-2"), items);
    }

    @Test
    public void testSubscribeFailure() {
        JsonRPCClientException e = Assertions.assertThrows(JsonRPCClientException.class,
                () -> client.subscribe("MultiResource#failing", String.class)
                        .collect().asList().await().atMost(Duration.ofSeconds(10)));
        Assertions.assertTrue(e.getMessage().contains("Multi test error"), e.getMessage());
    }

    @Test
    public void testSubscribeCancelUnsubscribes() {
        List<String> items = client.subscribe("MultiResource#ticking", String.class)
                .select().first(3)
                .collect().asList().await().atMost(Duration.ofSeconds(10));
        Assertions.assertEquals(List.of("tick-0", "tick-1", "tick-2"), items);

        // The connection is still usable after cancelling the subscription
        String result = client.call("HelloResource#hello", String.class);
        Assertions.assertTrue(result.startsWith("Hello ["), result);
    }
}
