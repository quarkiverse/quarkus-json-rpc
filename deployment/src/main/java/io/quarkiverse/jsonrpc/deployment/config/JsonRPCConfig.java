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
     * Configuration properties for the health check
     */
    @WithName("health")
    JsonRPCHealthConfig health();

    /**
     * Configuration properties for the OpenRPC service discovery document
     */
    @WithName("openrpc")
    JsonRPCOpenRPCConfig openrpc();
}
