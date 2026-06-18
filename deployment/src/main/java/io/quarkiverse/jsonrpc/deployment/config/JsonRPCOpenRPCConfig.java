package io.quarkiverse.jsonrpc.deployment.config;

import io.smallrye.config.WithDefault;

public interface JsonRPCOpenRPCConfig {

    /**
     * Whether the OpenRPC service discovery document is enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * HTTP path for the OpenRPC service discovery document.
     */
    @WithDefault("/json-rpc/openrpc.json")
    String path();
}
