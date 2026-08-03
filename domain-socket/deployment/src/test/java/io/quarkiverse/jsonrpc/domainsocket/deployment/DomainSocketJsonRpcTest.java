package io.quarkiverse.jsonrpc.domainsocket.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkiverse.jsonrpc.app.MultiResource;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.parsetools.RecordParser;

public class DomainSocketJsonRpcTest {

    static final String SOCKET_PATH = "/tmp/quarkus-json-rpc-test.sock";

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, MultiResource.class);
            })
            .overrideConfigKey("quarkus.json-rpc.domain-socket.enabled", "true")
            .overrideConfigKey("quarkus.json-rpc.domain-socket.path", SOCKET_PATH);

    static Vertx clientVertx;

    @BeforeAll
    static void setupVertx() {
        clientVertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    }

    @AfterAll
    static void teardownVertx() {
        if (clientVertx != null) {
            clientVertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @Test
    public void testBasicRequest() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 1, "method", "HelloResource#hello");
        JsonObject response = sendRequest(request);
        Assertions.assertEquals(1, response.getInteger("id"));
        Assertions.assertNotNull(response.getString("result"));
        Assertions.assertTrue(response.getString("result").startsWith("Hello ["));
    }

    @Test
    public void testRequestWithNamedParams() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 2, "method", "HelloResource#hello",
                "params", JsonObject.of("name", "World"));
        JsonObject response = sendRequest(request);
        Assertions.assertEquals(2, response.getInteger("id"));
        Assertions.assertTrue(response.getString("result").startsWith("Hello World ["));
    }

    @Test
    public void testRequestWithPositionalParams() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 3, "method", "HelloResource#hello",
                "params", JsonArray.of("Koos"));
        JsonObject response = sendRequest(request);
        Assertions.assertEquals(3, response.getInteger("id"));
        Assertions.assertTrue(response.getString("result").startsWith("Hello Koos ["));
    }

    @Test
    public void testMethodNotFound() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 4, "method", "NoSuch#method");
        JsonObject response = sendRequest(request);
        Assertions.assertEquals(4, response.getInteger("id"));
        JsonObject error = response.getJsonObject("error");
        Assertions.assertNotNull(error);
        Assertions.assertEquals(-32601, error.getInteger("code"));
    }

    @Test
    public void testParseError() throws Exception {
        LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
        NetSocket socket = connect(messages);
        try {
            socket.write("not valid json\n");
            String raw = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(raw);
            JsonObject response = new JsonObject(raw);
            JsonObject error = response.getJsonObject("error");
            Assertions.assertNotNull(error);
            Assertions.assertEquals(-32700, error.getInteger("code"));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testBatchRequest() throws Exception {
        JsonArray batch = JsonArray.of(
                JsonObject.of("jsonrpc", "2.0", "id", 10, "method", "HelloResource#hello"),
                JsonObject.of("jsonrpc", "2.0", "id", 11, "method", "HelloResource#hello",
                        "params", JsonObject.of("name", "Batch")));
        LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
        NetSocket socket = connect(messages);
        try {
            socket.write(batch.encode() + "\n");
            String raw = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(raw);
            JsonArray responses = new JsonArray(raw);
            Assertions.assertEquals(2, responses.size());
            JsonObject r1 = responses.getJsonObject(0);
            JsonObject r2 = responses.getJsonObject(1);
            Assertions.assertEquals(10, r1.getInteger("id"));
            Assertions.assertEquals(11, r2.getInteger("id"));
            Assertions.assertTrue(r1.getString("result").startsWith("Hello ["));
            Assertions.assertTrue(r2.getString("result").startsWith("Hello Batch ["));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testMultiSubscription() throws Exception {
        LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
        NetSocket socket = connect(messages);
        try {
            JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 20, "method", "MultiResource#items");
            socket.write(request.encode() + "\n");

            // Ack with subscription ID
            String ackRaw = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(ackRaw, "Expected ack");
            JsonObject ack = new JsonObject(ackRaw);
            Assertions.assertEquals(20, ack.getInteger("id"));
            String subscriptionId = ack.getString("result");
            Assertions.assertNotNull(subscriptionId);

            // 3 items + completion
            List<String> items = new ArrayList<>();
            boolean completed = false;
            for (int i = 0; i < 4; i++) {
                String raw = messages.poll(10, TimeUnit.SECONDS);
                Assertions.assertNotNull(raw, "Expected notification " + i);
                JsonObject notification = new JsonObject(raw);
                Assertions.assertEquals("subscription", notification.getString("method"));
                JsonObject params = notification.getJsonObject("params");
                Assertions.assertEquals(subscriptionId, params.getString("subscription"));
                if (params.containsKey("result")) {
                    items.add(params.getString("result"));
                } else if (params.containsKey("complete")) {
                    completed = true;
                }
            }
            Assertions.assertEquals(List.of("item-0", "item-1", "item-2"), items);
            Assertions.assertTrue(completed, "Expected completion notification");
        } finally {
            socket.close();
        }
    }

    @Test
    public void testUniMethod() throws Exception {
        JsonObject request = JsonObject.of("jsonrpc", "2.0", "id", 30, "method", "HelloResource#helloUni",
                "params", JsonObject.of("name", "Async"));
        JsonObject response = sendRequest(request);
        Assertions.assertEquals(30, response.getInteger("id"));
        Assertions.assertTrue(response.getString("result").startsWith("Hello Async ["));
    }

    private JsonObject sendRequest(JsonObject request) throws Exception {
        LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
        NetSocket socket = connect(messages);
        try {
            socket.write(request.encode() + "\n");
            String raw = messages.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(raw, "Expected response");
            return new JsonObject(raw);
        } finally {
            socket.close();
        }
    }

    private NetSocket connect(LinkedBlockingDeque<String> messages) throws Exception {
        NetClient client = clientVertx.createNetClient();
        CompletableFuture<NetSocket> connected = new CompletableFuture<>();

        client.connect(SocketAddress.domainSocketAddress(SOCKET_PATH))
                .onComplete(r -> {
                    if (r.succeeded()) {
                        NetSocket socket = r.result();
                        RecordParser parser = RecordParser.newDelimited("\n", buffer -> {
                            String line = buffer.toString().trim();
                            if (!line.isEmpty()) {
                                messages.add(line);
                            }
                        });
                        socket.handler(parser);
                        connected.complete(socket);
                    } else {
                        connected.completeExceptionally(r.cause());
                    }
                });

        return connected.get(5, TimeUnit.SECONDS);
    }
}
