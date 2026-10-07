package io.quarkiverse.jsonrpc.runtime.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.JsonRPCConnection;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

public class JsonRPCCodec {
    private static final Logger LOG = Logger.getLogger(JsonRPCCodec.class);
    private final ObjectMapper objectMapper;
    private volatile BiConsumer<JsonRPCConnection, String> messageLogListener;

    public JsonRPCCodec(ObjectMapper originalObjectMapper) {
        // we should never change settings of the original ObjectMapper as they could have global impact
        this.objectMapper = originalObjectMapper.rebuild()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    public void setMessageLogListener(BiConsumer<JsonRPCConnection, String> listener) {
        this.messageLogListener = listener;
    }

    public JsonNode parseJson(String json) throws JacksonException {
        return objectMapper.readTree(json);
    }

    public JsonRPCRequest readRequest(JsonNode jsonNode) {
        return new JsonRPCRequest(objectMapper, jsonNode);
    }

    public void writeResponse(JsonRPCConnection connection, JsonRPCResponse<?> response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            connection.writeTextMessage(json);
            logOutgoing(connection, json);
        } catch (JacksonException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writeBatchResponse(JsonRPCConnection connection, List<JsonRPCResponse<?>> responses) {
        try {
            String json = objectMapper.writeValueAsString(responses);
            connection.writeTextMessage(json);
            logOutgoing(connection, json);
        } catch (JacksonException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writeSubscriptionItem(JsonRPCConnection connection, String subscriptionId, Object item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(JsonRPCKeys.SUBSCRIPTION, subscriptionId);
        params.put(JsonRPCKeys.RESULT, item);
        writeNotification(connection, new JsonRPCNotification(JsonRPCKeys.SUBSCRIPTION, params));
    }

    public void writeSubscriptionError(JsonRPCConnection connection, String subscriptionId,
            JsonRPCResponse.Error error) {
        Map<String, Object> errorDetail = new LinkedHashMap<>();
        errorDetail.put(JsonRPCKeys.CODE, error.code);
        errorDetail.put(JsonRPCKeys.MESSAGE, error.message);
        if (error.data != null) {
            errorDetail.put(JsonRPCKeys.DATA, error.data);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put(JsonRPCKeys.SUBSCRIPTION, subscriptionId);
        params.put(JsonRPCKeys.ERROR, errorDetail);
        writeNotification(connection, new JsonRPCNotification(JsonRPCKeys.SUBSCRIPTION, params));
    }

    public void writeSubscriptionComplete(JsonRPCConnection connection, String subscriptionId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(JsonRPCKeys.SUBSCRIPTION, subscriptionId);
        params.put(JsonRPCKeys.COMPLETE, true);
        writeNotification(connection, new JsonRPCNotification(JsonRPCKeys.SUBSCRIPTION, params));
    }

    public void writeNotification(JsonRPCConnection connection, JsonRPCNotification notification) {
        if (connection.isClosed()) {
            LOG.debugf("Dropping notification for closed connection: method=%s", notification.method);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(notification);
            connection.writeTextMessage(json);
            logOutgoing(connection, json);
        } catch (JacksonException ex) {
            LOG.errorf(ex, "Failed to serialize JSON-RPC notification: method=%s", notification.method);
            throw new RuntimeException(
                    "Failed to serialize JSON-RPC notification for method '" + notification.method + "'", ex);
        }
    }

    private void logOutgoing(JsonRPCConnection connection, String json) {
        BiConsumer<JsonRPCConnection, String> listener = this.messageLogListener;
        if (listener != null) {
            listener.accept(connection, json);
        }
    }
}
