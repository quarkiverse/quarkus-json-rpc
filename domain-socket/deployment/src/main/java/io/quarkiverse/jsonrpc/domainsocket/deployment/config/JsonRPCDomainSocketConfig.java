package io.quarkiverse.jsonrpc.domainsocket.deployment.config;

import io.smallrye.config.WithDefault;

public interface JsonRPCDomainSocketConfig {

    /**
     * Enable JSON-RPC over a Unix domain socket using JSONL framing (newline-delimited JSON).
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Path to the Unix domain socket file.
     */
    @WithDefault("/tmp/quarkus-json-rpc.sock")
    String path();
}
