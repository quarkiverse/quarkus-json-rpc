package io.quarkiverse.jsonrpc.runtime.model;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.vertx.core.http.ServerWebSocket;

public class JsonRPCCodec {
    private static final Logger LOG = Logger.getLogger(JsonRPCCodec.class);
    private final ObjectMapper objectMapper;

    public JsonRPCCodec(ObjectMapper originalObjectMapper) {
        this.objectMapper = originalObjectMapper.copy(); // we should never change settings of the original ObjectMapper as they could have global impact
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public JsonRPCRequest readRequest(String json) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            return new JsonRPCRequest(objectMapper, jsonNode);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writeResponse(ServerWebSocket socket, int id, Object object) {
        writeResponse(socket, new JsonRPCResponse(id, object));
    }

    public void writeMethodNotFoundResponse(ServerWebSocket socket, int id, String jsonRpcMethodName) {
        writeResponse(socket, new JsonRPCResponse(id,
                new JsonRPCResponse.Error(JsonRPCKeys.METHOD_NOT_FOUND, "Method [" + jsonRpcMethodName + "] not found")));
    }

    public void writeErrorResponse(ServerWebSocket socket, int id, String jsonRpcMethodName, Throwable exception) {
        writeErrorResponse(socket, id, JsonRPCKeys.INTERNAL_ERROR, jsonRpcMethodName, exception);
    }

    public void writeErrorResponse(ServerWebSocket socket, int id, int code, String jsonRpcMethodName,
            Throwable exception) {
        LOG.error("Error in JsonRPC Call", exception);
        writeResponse(socket, new JsonRPCResponse(id,
                new JsonRPCResponse.Error(code,
                        "Method [" + jsonRpcMethodName + "] failed: " + exception.getMessage())));
    }

    public void writeSubscriptionItem(ServerWebSocket socket, String subscriptionId, Object item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(JsonRPCKeys.SUBSCRIPTION, subscriptionId);
        params.put(JsonRPCKeys.RESULT, item);
        writeNotification(socket, new JsonRPCNotification(JsonRPCKeys.SUBSCRIPTION, params));
    }

    public void writeSubscriptionError(ServerWebSocket socket, String subscriptionId, String methodName,
            Throwable exception) {
        LOG.error("Error in JsonRPC subscription", exception);
        Map<String, Object> errorDetail = new LinkedHashMap<>();
        errorDetail.put(JsonRPCKeys.CODE, JsonRPCKeys.INTERNAL_ERROR);
        errorDetail.put(JsonRPCKeys.MESSAGE, "Method [" + methodName + "] failed: " + exception.getMessage());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put(JsonRPCKeys.SUBSCRIPTION, subscriptionId);
        params.put(JsonRPCKeys.ERROR, errorDetail);
        writeNotification(socket, new JsonRPCNotification(JsonRPCKeys.SUBSCRIPTION, params));
    }

    public void writeSubscriptionComplete(ServerWebSocket socket, String subscriptionId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(JsonRPCKeys.SUBSCRIPTION, subscriptionId);
        params.put(JsonRPCKeys.COMPLETE, true);
        writeNotification(socket, new JsonRPCNotification(JsonRPCKeys.SUBSCRIPTION, params));
    }

    private void writeResponse(ServerWebSocket socket, JsonRPCResponse response) {
        try {
            socket.writeTextMessage(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writeNotification(ServerWebSocket socket, JsonRPCNotification notification) {
        if (socket.isClosed()) {
            LOG.debugf("Dropping notification for closed WebSocket: method=%s", notification.method);
            return;
        }
        try {
            socket.writeTextMessage(objectMapper.writeValueAsString(notification));
        } catch (JsonProcessingException ex) {
            LOG.errorf(ex, "Failed to serialize JSON-RPC notification: method=%s", notification.method);
            throw new RuntimeException(
                    "Failed to serialize JSON-RPC notification for method '" + notification.method + "'", ex);
        }
    }
}
