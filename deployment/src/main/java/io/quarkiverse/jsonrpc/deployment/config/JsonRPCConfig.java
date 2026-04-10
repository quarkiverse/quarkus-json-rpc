package io.quarkiverse.jsonrpc.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * JsonRPC Configuration
 */
@ConfigMapping(prefix = "quarkus.json-rpc")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface JsonRPCConfig {

    /**
     * Configuration properties for the JsonRPC Websocket
     */
    @WithName("web-socket")
    JsonRPCWebSocketConfig webSocket();

    /**
     * Configuration properties for the JavaScript client proxy generation
     */
    JsonRPCClientConfig client();
}
