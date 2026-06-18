package io.quarkiverse.jsonrpc.deployment.config;

import io.smallrye.config.WithDefault;

public interface JsonRPCHealthConfig {

    /**
     * Whether the JSON-RPC health check is enabled.
     */
    @WithDefault("true")
    boolean enabled();
}
