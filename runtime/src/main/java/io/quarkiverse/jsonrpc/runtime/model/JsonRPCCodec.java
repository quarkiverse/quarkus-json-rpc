package io.quarkiverse.jsonrpc.runtime.model;

import org.jboss.logging.Logger;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class JsonRPCCodec {
    private static final Logger LOG = Logger.getLogger(JsonRPCCodec.class);

    public JsonRPCCodec() {
    }

    public JsonRPCRequest readRequest(String json) {
        return new JsonRPCRequest((JsonObject) Json.decodeValue(json));
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
        socker.writeTextMessage(Json.encodePrettily(response));
    }
}
