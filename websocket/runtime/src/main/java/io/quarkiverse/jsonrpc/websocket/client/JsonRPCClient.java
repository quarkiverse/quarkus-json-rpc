package io.quarkiverse.jsonrpc.websocket.client;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A Java client for calling JSON-RPC services over WebSocket.
 * <p>
 * One client holds one WebSocket connection; calls and subscriptions are multiplexed over it.
 *
 * <pre>
 * try (JsonRPCClient client = JsonRPCClient.connect(vertx, URI.create("ws://localhost:8080/json-rpc"))) {
 *     String hello = client.call("GreetingService#hello", Map.of("name", "World"), String.class);
 * }
 * </pre>
 */
public class JsonRPCClient implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(JsonRPCClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final WebSocketClient webSocketClient;
    private final WebSocket webSocket;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, Consumer<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> subscriptions = new ConcurrentHashMap<>();

    private JsonRPCClient(WebSocketClient webSocketClient, WebSocket webSocket, ObjectMapper objectMapper,
            Duration timeout) {
        this.webSocketClient = webSocketClient;
        this.webSocket = webSocket;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
        webSocket.textMessageHandler(this::onMessage);
        webSocket.closeHandler(v -> failAll(new IllegalStateException("JSON-RPC connection closed")));
    }

    public static JsonRPCClient connect(Vertx vertx, URI uri) {
        return connect(vertx, uri, JsonMapper.builder().build(), DEFAULT_TIMEOUT);
    }

    /**
     * Connect to a JSON-RPC WebSocket endpoint, e.g. {@code ws://localhost:8080/json-rpc}. Blocks until connected, so
     * do not call it on the event loop; use {@link #connectAsync} there.
     */
    public static JsonRPCClient connect(Vertx vertx, URI uri, ObjectMapper objectMapper, Duration timeout) {
        return connectAsync(vertx, uri, objectMapper, timeout).await().atMost(timeout);
    }

    public static Uni<JsonRPCClient> connectAsync(Vertx vertx, URI uri, ObjectMapper objectMapper, Duration timeout) {
        WebSocketClient webSocketClient = vertx.createWebSocketClient();
        WebSocketConnectOptions options = new WebSocketConnectOptions().setAbsoluteURI(uri.toString());
        return Uni.createFrom().completionStage(() -> webSocketClient.connect(options).toCompletionStage())
                .onItem().transform(ws -> new JsonRPCClient(webSocketClient, ws, objectMapper, timeout))
                .onFailure().call(() -> Uni.createFrom().completionStage(() -> webSocketClient.close().toCompletionStage()));
    }

    /**
     * Call a method with named parameters and wait for the result.
     */
    public <T> T call(String method, Map<String, ?> params, Class<T> resultType) {
        return callAsync(method, params, resultType).await().atMost(timeout);
    }

    /**
     * Call a method with positional parameters and wait for the result.
     */
    public <T> T call(String method, Class<T> resultType, Object... params) {
        return callAsync(method, resultType, params).await().atMost(timeout);
    }

    public <T> Uni<T> callAsync(String method, Map<String, ?> params, Class<T> resultType) {
        return callAsync(method, (Object) params, resultType);
    }

    public <T> Uni<T> callAsync(String method, Class<T> resultType, Object... params) {
        return callAsync(method, (Object) params, resultType);
    }

    /**
     * Subscribe to a method returning a {@code Multi} using named parameters. Cancelling the returned {@link Multi}
     * unsubscribes on the server.
     */
    public <T> Multi<T> subscribe(String method, Map<String, ?> params, Class<T> itemType) {
        return subscribe(method, (Object) params, itemType);
    }

    public <T> Multi<T> subscribe(String method, Class<T> itemType, Object... params) {
        return subscribe(method, (Object) params, itemType);
    }

    /**
     * Send a JSON-RPC notification (a request without id); no response is expected.
     */
    public void notify(String method, Object... params) {
        send(request(null, method, params));
    }

    @Override
    public void close() {
        webSocketClient.close().await();
    }

    private <T> Uni<T> callAsync(String method, Object params, Class<T> resultType) {
        return Uni.createFrom().<JsonNode> emitter(emitter -> {
            long id = ids.incrementAndGet();
            pending.put(id, response -> {
                JsonNode error = response.get(JsonRPCKeys.ERROR);
                if (error != null && !error.isNull()) {
                    emitter.fail(toException(error));
                } else {
                    emitter.complete(response.get(JsonRPCKeys.RESULT));
                }
            });
            emitter.onTermination(() -> pending.remove(id));
            send(request(id, method, params));
        }).onItem().transform(result -> convert(result, resultType));
    }

    private <T> Multi<T> subscribe(String method, Object params, Class<T> itemType) {
        return Multi.createFrom().<T> emitter(emitter -> {
            long id = ids.incrementAndGet();
            String[] subscriptionId = new String[1];
            pending.put(id, response -> {
                JsonNode error = response.get(JsonRPCKeys.ERROR);
                if (error != null && !error.isNull()) {
                    emitter.fail(toException(error));
                    return;
                }
                // Registered on the message handler thread, so no notification for this subscription can be missed
                subscriptionId[0] = response.get(JsonRPCKeys.RESULT).asString();
                subscriptions.put(subscriptionId[0], notification -> onSubscriptionMessage(notification, emitter,
                        itemType));
            });
            emitter.onTermination(() -> {
                pending.remove(id);
                if (subscriptionId[0] != null && subscriptions.remove(subscriptionId[0]) != null) {
                    // Cancelled by the subscriber while the server is still streaming
                    send(request(ids.incrementAndGet(), JsonRPCKeys.UNSUBSCRIBE, new Object[] { subscriptionId[0] }));
                }
            });
            send(request(id, method, params));
        });
    }

    private <T> void onSubscriptionMessage(JsonNode params, MultiEmitter<? super T> emitter, Class<T> itemType) {
        String subscriptionId = params.path(JsonRPCKeys.SUBSCRIPTION).asString();
        JsonNode error = params.get(JsonRPCKeys.ERROR);
        if (error != null && !error.isNull()) {
            subscriptions.remove(subscriptionId);
            emitter.fail(toException(error));
        } else if (params.path(JsonRPCKeys.COMPLETE).asBoolean(false)) {
            subscriptions.remove(subscriptionId);
            emitter.complete();
        } else {
            try {
                emitter.emit(convert(params.get(JsonRPCKeys.RESULT), itemType));
            } catch (RuntimeException e) {
                emitter.fail(e);
            }
        }
    }

    private void onMessage(String message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Ignoring unparsable JSON-RPC message: %s", message);
            return;
        }
        if (node.isArray()) {
            node.forEach(this::onMessage);
        } else {
            onMessage(node);
        }
    }

    private void onMessage(JsonNode node) {
        JsonNode method = node.get(JsonRPCKeys.METHOD);
        if (method != null) {
            if (JsonRPCKeys.SUBSCRIPTION.equals(method.asString())) {
                JsonNode params = node.get(JsonRPCKeys.PARAMS);
                Consumer<JsonNode> subscription = subscriptions.get(params.path(JsonRPCKeys.SUBSCRIPTION).asString());
                if (subscription != null) {
                    subscription.accept(params);
                }
            } else {
                LOG.debugf("Ignoring JSON-RPC notification [%s]", method.asString());
            }
            return;
        }
        JsonNode id = node.get(JsonRPCKeys.ID);
        if (id != null && id.canConvertToLong()) {
            Consumer<JsonNode> handler = pending.remove(id.asLong());
            if (handler != null) {
                handler.accept(node);
            }
        }
    }

    private void failAll(Throwable cause) {
        JsonNode error = objectMapper.createObjectNode()
                .put(JsonRPCKeys.CODE, JsonRPCKeys.INTERNAL_ERROR)
                .put(JsonRPCKeys.MESSAGE, cause.getMessage());
        ObjectNode failure = objectMapper.createObjectNode();
        failure.set(JsonRPCKeys.ERROR, error);
        Map.copyOf(pending).values().forEach(handler -> handler.accept(failure));
        pending.clear();
        Map.copyOf(subscriptions).values().forEach(handler -> handler.accept(failure));
        subscriptions.clear();
    }

    private void send(ObjectNode request) {
        webSocket.writeTextMessage(objectMapper.writeValueAsString(request));
    }

    private ObjectNode request(Long id, String method, Object params) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put(JsonRPCKeys.JSONRPC, JsonRPCKeys.VERSION);
        if (id != null) {
            request.put(JsonRPCKeys.ID, id);
        }
        request.put(JsonRPCKeys.METHOD, method);
        if (params instanceof Object[] array && array.length > 0) {
            request.set(JsonRPCKeys.PARAMS, objectMapper.valueToTree(array));
        } else if (params instanceof Map<?, ?> map && !map.isEmpty()) {
            request.set(JsonRPCKeys.PARAMS, objectMapper.valueToTree(map));
        }
        return request;
    }

    private <T> T convert(JsonNode result, Class<T> type) {
        if (result == null || result.isNull()) {
            return null;
        }
        return objectMapper.treeToValue(result, type);
    }

    private JsonRPCClientException toException(JsonNode error) {
        JsonNode data = error.get(JsonRPCKeys.DATA);
        return new JsonRPCClientException(error.path(JsonRPCKeys.CODE).asInt(), error.path(JsonRPCKeys.MESSAGE).asString(),
                data == null || data.isNull() ? null : objectMapper.treeToValue(data, Object.class));
    }
}
