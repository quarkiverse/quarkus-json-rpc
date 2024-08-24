package io.quarkiverse.jsonrpc.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.WithName;

/**
 * JsonRPC Configuration
 */
@ConfigRoot(name = "json-rpc", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class JsonRpcConfig {

    /**
     * Configuration properties for the JsonRPC Websocket
     */
    @WithName("web-socket")
    public JsonRPCWebSocketConfig webSocket;

    @WithName("open-rpc")
    public JsonRPCOpenRpcConfig openRPC;
}
