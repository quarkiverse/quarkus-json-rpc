package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;

import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonRpcParent {

    private final AtomicInteger count = new AtomicInteger(0);

    @Inject
    Vertx vertx;

    @TestHTTPResource("quarkus/json-rpc")
    URI jsonRpcUri;

    protected String getJsonRpcResponse(String providedInput) throws Exception {
        return getJsonRpcResponse(providedInput, String.class);
    }

    protected <T> T getJsonRpcResponse(String providedInput, Class<T> responseType) throws Exception {
        return getJsonRpcResponse(providedInput, responseType, Map.of());
    }

    protected String getJsonRpcResponse(String providedInput, Object[] params) throws Exception {
        return getJsonRpcResponse(providedInput, String.class, params);
    }

    protected <T> T getJsonRpcResponse(String providedInput, Class<T> responseType, Object[] params) throws Exception {
        int id = count.incrementAndGet();
        return jsonRpcResponse(id, responseType, (ws, queue) -> {
            ws.textMessageHandler(msg -> {
                queue.add(msg);
            });
            ws.writeTextMessage(getJsonRPCRequest(id, providedInput, params));
        });
    }

    protected String getJsonRpcResponse(String providedInput, Map<String, Object> params) throws Exception {
        return getJsonRpcResponse(providedInput, String.class, params);
    }

    protected <T> T getJsonRpcResponse(String providedInput, Class<T> responseType, Map<String, Object> params)
            throws Exception {
        int id = count.incrementAndGet();
        return jsonRpcResponse(id, responseType, (ws, queue) -> {
            ws.textMessageHandler(msg -> {
                queue.add(msg);
            });
            ws.writeTextMessage(getJsonRPCRequest(id, providedInput, params));
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T jsonRpcResponse(int expectedId, Class<T> responseType, BiConsumer<WebSocket, Queue<String>> action)
            throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            LinkedBlockingDeque<String> message = new LinkedBlockingDeque<>();
            client
                    .connect(jsonRpcUri.getPort(), jsonRpcUri.getHost(), jsonRpcUri.getPath())
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            WebSocket ws = r.result();
                            action.accept(ws, message);
                        } else {
                            throw new IllegalStateException(r.cause());
                        }
                    });

            String response = message.poll(10, TimeUnit.SECONDS);
            JsonObject jsonResponse = Json.decodeValue(response, JsonObject.class);
            int id = jsonResponse.getInteger("id");
            Assertions.assertEquals(expectedId, id);
            String error = jsonResponse.getString("error");
            if (error != null) {
                Assertions.fail(error);
            } else {
                if (responseType.equals(String.class)) {
                    String result = jsonResponse.getString("result");
                    return (T) result;
                    // TODO: Add other primative types
                } else if (responseType.equals(JsonObject.class)) {
                    JsonObject result = jsonResponse.getJsonObject("result");
                    return (T) result;
                } else {
                    throw new RuntimeException("Unsupported type " + responseType);
                }
            }
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
        return null;
    }

    private String getJsonRPCRequest(int id, String method, Map<String, Object> params) {
        JsonObject jsonObject = JsonObject.of("jsonrpc", "2.0", "id", id, "method", method);
        if (params != null && !params.isEmpty()) {
            JsonObject p = JsonObject.mapFrom(params);
            jsonObject.put("params", p);
        }
        return jsonObject.encodePrettily();
    }

    private String getJsonRPCRequest(int id, String method, Object[] params) {
        JsonObject jsonObject = JsonObject.of("jsonrpc", "2.0", "id", id, "method", method);
        if (params != null && params.length > 0) {
            JsonArray a = JsonArray.of(params);
            jsonObject.put("params", a);
        }
        return jsonObject.encodePrettily();
    }
}
