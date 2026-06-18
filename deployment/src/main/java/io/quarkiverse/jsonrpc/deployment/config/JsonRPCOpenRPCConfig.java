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

    /**
     * Title of the API in the OpenRPC {@code info} object.
     */
    @WithDefault("JSON-RPC API")
    String title();

    /**
     * Version of the API in the OpenRPC {@code info} object.
     */
    @WithDefault("1.0.0")
    String version();

    /**
     * Whether to use simple class names (e.g. {@code Pojo}) instead of fully-qualified names
     * (e.g. {@code com.example.Pojo}) as schema keys in the {@code components/schemas} section.
     * Simple names are shorter but may collide when different packages contain classes with the same name.
     */
    @WithDefault("false")
    boolean schemaSimpleNames();
}
