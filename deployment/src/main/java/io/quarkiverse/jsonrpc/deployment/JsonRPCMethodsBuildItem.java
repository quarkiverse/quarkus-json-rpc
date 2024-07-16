package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkus.builder.item.SimpleBuildItem;

public final class JsonRPCMethodsBuildItem extends SimpleBuildItem {
    private final Map<JsonRPCMethodName, JsonRPCMethod> methodsMap;

    public JsonRPCMethodsBuildItem(Map<JsonRPCMethodName, JsonRPCMethod> methodsMap) {
        this.methodsMap = methodsMap;
    }

    public Map<JsonRPCMethodName, JsonRPCMethod> getMethodsMap() {
        return methodsMap;
    }
}
