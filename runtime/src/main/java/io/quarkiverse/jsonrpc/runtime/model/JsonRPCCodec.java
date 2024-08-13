package io.quarkiverse.jsonrpc.runtime.model;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.quarkus.arc.Arc;
import io.vertx.core.http.ServerWebSocket;

public class JsonRPCCodec {
    private static final Logger LOG = Logger.getLogger(JsonRPCCodec.class);
    private final ObjectMapper objectMapper;

    public JsonRPCCodec() {
        this.objectMapper = Arc.container().select(ObjectMapper.class).get();
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
        LOG.error("Error in JsonRPC Call", exception);
        writeResponse(socket, new JsonRPCResponse(id,
                new JsonRPCResponse.Error(JsonRPCKeys.INTERNAL_ERROR,
                        "Method [" + jsonRpcMethodName + "] failed: " + exception.getMessage())));
    }

    private void writeResponse(ServerWebSocket socker, JsonRPCResponse response) {
        try {
            socker.writeTextMessage(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }
}
