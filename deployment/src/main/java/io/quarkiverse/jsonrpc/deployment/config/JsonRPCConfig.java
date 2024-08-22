package io.quarkiverse.jsonrpc.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.WithName;

/**
 * JsonRPC Configuration
 */
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public class JsonRPCConfig {

    /**
     * Configuration properties for the JsonRPC Websocket
     */
    @WithName("web-socket")
    public JsonRPCWebSocketConfig webSocket;

    @WithName("open-rpc")
    public JsonRPCOpenRPCConfig openRPC;
}
