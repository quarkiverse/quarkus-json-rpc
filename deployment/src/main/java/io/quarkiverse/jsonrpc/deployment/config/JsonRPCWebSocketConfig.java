package io.quarkiverse.jsonrpc.deployment.config;

import io.smallrye.config.WithDefault;

public interface JsonRPCWebSocketConfig {

    /**
     * Enable JsonRPC Websocket
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * HTTP Path for the JsonRPC Websocket
     */
    @WithDefault("/quarkus/json-rpc")
    String path();
}
