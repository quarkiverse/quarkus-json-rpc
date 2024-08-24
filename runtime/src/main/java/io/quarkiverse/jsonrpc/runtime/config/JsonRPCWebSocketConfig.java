package io.quarkiverse.jsonrpc.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class JsonRPCWebSocketConfig {

    /**
     * Enable JsonRPC Websocket
     */
    @ConfigItem(defaultValue = "true")
    public boolean enabled;
    /**
     * HTTP Path for the JsonRPC Websocket
     */
    @ConfigItem(defaultValue = "/quarkus/json-rpc")
    public String path;
}
