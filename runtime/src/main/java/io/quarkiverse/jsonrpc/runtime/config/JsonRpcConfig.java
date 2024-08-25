package io.quarkiverse.jsonrpc.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * JsonRPC Configuration
 */
@ConfigMapping(prefix = "quarkus.json-rpc")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface JsonRpcConfig {

    /**
     * Configuration properties for the JsonRPC Websocket
     */
    @WithName("web-socket")
    public JsonRPCWebSocketConfig webSocket();

    /**
     * Configuration properties for the OpenRPC Schema document and DevUIs
     */
    @WithName("open-rpc")
    public JsonRPCOpenRpcConfig openRpc();
}
