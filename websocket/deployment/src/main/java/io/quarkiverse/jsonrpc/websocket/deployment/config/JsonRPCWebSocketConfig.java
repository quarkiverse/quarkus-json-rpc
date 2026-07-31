package io.quarkiverse.jsonrpc.websocket.deployment.config;

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
    @WithDefault("/json-rpc")
    String path();
}
