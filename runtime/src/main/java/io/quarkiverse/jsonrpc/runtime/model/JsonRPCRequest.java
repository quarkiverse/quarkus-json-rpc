package io.quarkiverse.jsonrpc.runtime.model;

import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.ID;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.JSONRPC;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.METHOD;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.PARAMS;
import static io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys.VERSION;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonRPCRequest {
    private final ObjectMapper objectMapper;
    private final JsonNode jsonNode;
    private final ParamOption paramOption;

    JsonRPCRequest(ObjectMapper objectMapper, JsonNode jsonNode) {
        this.objectMapper = objectMapper;
        this.jsonNode = jsonNode;
        this.paramOption = parametersProvidedAs();
    }

    public int getId() {
        return jsonNode.get(ID).asInt();
    }

    public String getJsonrpc() {
        String value = jsonNode.get(JSONRPC).asText();
        if (value != null) {
            return value;
        }
        return VERSION;
    }

    public String getMethod() {
        return jsonNode.get(METHOD).asText();
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
        if (paramOption != null && paramOption.equals(ParamOption.OBJECT)) {
            ObjectNode paramsObject = jsonNode.withObject(PARAMS);
            if (paramsObject != null && paramsObject.size() > 0) {
                try {
                    return objectMapper.treeToValue(paramsObject, Map.class);
                } catch (IllegalArgumentException | JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return null;
    }

    public Object[] getPositionedParams() {
        if (paramOption != null && paramOption.equals(ParamOption.ARRAY)) {
            ArrayNode paramsObject = jsonNode.withArrayProperty(PARAMS);
            if (paramsObject != null && !paramsObject.isEmpty()) {
                Object[] objects = new Object[paramsObject.size()];
                for (int i = 0; i < paramsObject.size(); i++) {
                    JsonNode node = paramsObject.get(i);
                    try {
                        objects[i] = objectMapper.treeToValue(node, Object.class);
                    } catch (IllegalArgumentException | JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                return objects;
            }
        }
        return null;
    }

    public <T> T getNamedParam(String key, Class<T> paramType) {
        Map<?, ?> params = getNamedParams();
        if (params == null || !params.containsKey(key)) {
            return null;
        }
        Object o = params.get(key);
        return (T) bindParam(o, paramType);
    }

    public <T> T getPositionedParam(int pos, Class<T> paramType) {
        Object[] params = getPositionedParams();
        if (params == null || params.length == 0) {
            return null;
        }
        Object o = params[pos - 1];
        return (T) bindParam(o, paramType);
    }

    public Set<String> getNamedParamKeys() {
        Map<String, Object> namedParams = getNamedParams();
        if (namedParams != null && !namedParams.isEmpty()) {
            return namedParams.keySet();
        }
        return null;
    }

    private <T> Object bindParam(Object o, Class<T> paramType) {
        // If a complex object, we need to bind
        if (o.getClass().equals(LinkedHashMap.class)) {
            return objectMapper.convertValue(o, paramType);
        }
        return o;
    }

    private ParamOption parametersProvidedAs() {
        JsonNode value = jsonNode.get(PARAMS);
        if (value == null) {
            return null;
        }
        if (value.isArray()) {
            return ParamOption.ARRAY;
        }
        return ParamOption.OBJECT;
    }

    @Override
    public String toString() {
        return jsonNode.toPrettyString();
    }

    enum ParamOption {
        ARRAY,
        OBJECT
    }
}
