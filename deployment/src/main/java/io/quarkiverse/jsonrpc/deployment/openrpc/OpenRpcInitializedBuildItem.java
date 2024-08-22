package io.quarkiverse.jsonrpc.deployment.openrpc;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.RuntimeValue;

public final class OpenRpcInitializedBuildItem extends SimpleBuildItem {

    private final RuntimeValue<Boolean> initialized;

    public OpenRpcInitializedBuildItem(RuntimeValue<Boolean> initialized) {
        this.initialized = initialized;
    }

    public RuntimeValue<Boolean> getInitialized() {
        return initialized;
    }
}
