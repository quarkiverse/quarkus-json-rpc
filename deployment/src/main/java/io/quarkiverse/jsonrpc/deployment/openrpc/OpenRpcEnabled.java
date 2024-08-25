package io.quarkiverse.jsonrpc.deployment.openrpc;

import java.util.function.BooleanSupplier;

import io.quarkiverse.jsonrpc.runtime.config.JsonRpcConfig;

public class OpenRpcEnabled implements BooleanSupplier {

    public final JsonRpcConfig config;

    public OpenRpcEnabled(JsonRpcConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        return config.openRpc().enabled();
    }
}
