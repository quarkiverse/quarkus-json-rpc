package io.quarkiverse.jsonrpc.deployment;

import java.util.Map;
import java.util.Set;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkus.builder.item.SimpleBuildItem;

public final class JsonRPCMethodsBuildItem extends SimpleBuildItem {
    private final Map<JsonRPCMethodName, JsonRPCMethod> methodsMap;
    private final Set<String> extraPaths;
    private final Map<String, String> scopeToPath;

    public JsonRPCMethodsBuildItem(Map<JsonRPCMethodName, JsonRPCMethod> methodsMap,
            Set<String> extraPaths, Map<String, String> scopeToPath) {
        this.methodsMap = methodsMap;
        this.extraPaths = extraPaths != null ? Set.copyOf(extraPaths) : Set.of();
        this.scopeToPath = scopeToPath != null ? Map.copyOf(scopeToPath) : Map.of();
    }

    public Map<JsonRPCMethodName, JsonRPCMethod> getMethodsMap() {
        return methodsMap;
    }

    /**
     * @return additional WebSocket paths declared via {@code @JsonRPCApi(path = "...")}
     *         (does not include the default global path)
     */
    public Set<String> getExtraPaths() {
        return extraPaths;
    }

    /**
     * @return mapping from scope name to custom path, only for scopes with explicit paths
     */
    public Map<String, String> getScopeToPath() {
        return scopeToPath;
    }
}
