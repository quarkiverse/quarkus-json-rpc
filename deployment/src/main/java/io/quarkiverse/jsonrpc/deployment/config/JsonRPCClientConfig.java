package io.quarkiverse.jsonrpc.deployment.config;

import io.smallrye.config.WithDefault;

public interface JsonRPCClientConfig {

    /**
     * Generate a JavaScript client proxy for all discovered JSON-RPC endpoints.
     * When enabled, a static client library and a typed proxy module are generated
     * as web resources, along with an import map following the mvnpm convention.
     */
    @WithDefault("false")
    boolean enabled();
}
