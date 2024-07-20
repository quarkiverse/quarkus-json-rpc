package io.quarkiverse.jsonrpc.runtime.model;

import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.ID;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.JSONRPC;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.METHOD;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.PARAMS;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.VERSION;

import java.util.Map;
import java.util.Set;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonRPCRequest {

    private final JsonObject jsonObject;
    private final ParamOption paramOption;

    JsonRPCRequest(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
        this.paramOption = parametersProvidedAs();
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
        return hasNamedParams() || hasPositionedParams();
    }

    public boolean hasNamedParams() {
        return this.getNamedParams() != null;
    }

    public boolean hasPositionedParams() {
        return this.getPositionedParams() != null;
    }

    public Map<String, Object> getNamedParams() {
        if (paramOption.equals(ParamOption.OBJECT)) {
            JsonObject paramsObject = jsonObject.getJsonObject(PARAMS);
            if (paramsObject != null && paramsObject.getMap() != null && !paramsObject.getMap().isEmpty()) {
                return paramsObject.getMap();
            }
        }
        return null;
    }

    public Object[] getPositionedParams() {
        if (paramOption.equals(ParamOption.ARRAY)) {
            JsonArray paramsObject = jsonObject.getJsonArray(PARAMS);
            if (paramsObject != null && !paramsObject.isEmpty()) {
                return paramsObject.stream().toArray(Object[]::new);
            }
        }
        return null;
    }

    public <T> T getNamedParam(String key, Class<T> paramType) {
        Map<?, ?> params = getNamedParams();
        if (params == null || !params.containsKey(key)) {
            return null;
        }
        return (T) params.get(key);
    }

    public <T> T getPositionedParam(int pos, Class<T> paramType) {
        Object[] params = getPositionedParams();
        if (params == null || params.length == 0) {
            return null;
        }
        return (T) params[pos - 1];
    }

    public Set<String> getNamedParamKeys() {
        Map<String, Object> namedParams = getNamedParams();
        if (namedParams != null && !namedParams.isEmpty()) {
            return namedParams.keySet();
        }
        return null;
    }

    private ParamOption parametersProvidedAs() {

        Object value = jsonObject.getValue(PARAMS);
        if (value == null) {
            return null;
        } else if (value instanceof JsonObject) {
            return ParamOption.OBJECT;
        } else {
            return ParamOption.ARRAY;
        }
    }

    @Override
    public String toString() {
        return jsonObject.encodePrettily();
    }

    enum ParamOption {
        ARRAY,
        OBJECT
    }
}
