package io.quarkiverse.jsonrpc.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface JsonRPCWebSocketConfig {

    /**
     * Enable JsonRPC Websocket
     */
    @WithDefault("true")
    public boolean enabled();

    /**
     * HTTP Path for the JsonRPC Websocket
     */
    @WithDefault("quarkus/json-rpc")
    public String path();
}
