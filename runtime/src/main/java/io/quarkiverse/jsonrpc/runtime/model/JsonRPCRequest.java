package io.quarkiverse.jsonrpc.runtime.model;

import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.ID;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.JSONRPC;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.METHOD;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.PARAMS;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.VERSION;

import java.util.Map;

import io.vertx.core.json.JsonObject;

public class JsonRPCRequest {

    private final JsonObject jsonObject;

    JsonRPCRequest(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public int getId() {
        return jsonObject.getInteger(ID);
    }

    public String getJsonrpc() {
        String value = jsonObject.getString(JSONRPC);
        if (value != null) {
            return value;
        }
        return VERSION;
    }

    public String getMethod() {
        return jsonObject.getString(METHOD);
    }

    public boolean hasParams() {
        return this.getParams() != null;
    }

    public Map<?, ?> getParams() {
        JsonObject paramsObject = jsonObject.getJsonObject(PARAMS);
        if (paramsObject != null && paramsObject.getMap() != null && !paramsObject.getMap().isEmpty()) {
            return paramsObject.getMap();
        }
        return null;
    }

    public <T> T getParam(String key, Class<T> paramType) {
        Map<?, ?> params = getParams();
        if (params == null || !params.containsKey(key)) {
            return null;
        }
        return (T) params.get(key);
    }

    @Override
    public String toString() {
        return jsonObject.encodePrettily();
    }
}
