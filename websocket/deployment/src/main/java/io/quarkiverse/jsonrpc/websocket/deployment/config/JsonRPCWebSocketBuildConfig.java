package io.quarkiverse.jsonrpc.websocket.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * JsonRPC WebSocket Configuration
 */
@ConfigMapping(prefix = "quarkus.json-rpc")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface JsonRPCWebSocketBuildConfig {

    /**
     * Configuration properties for the JsonRPC WebSocket
     */
    @WithName("web-socket")
    JsonRPCWebSocketConfig webSocket();

    /**
     * Configuration properties for the JavaScript client proxy generation
     */
    @WithName("js-client")
    JsonRPCClientConfig jsClient();
}
