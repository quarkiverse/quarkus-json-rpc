package io.quarkiverse.jsonrpc.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface JsonRPCOpenRpcSchemaContactConfig {

    /**
     * Name of the contact
     */
    Optional<String> name();

    /**
     * URL to the contact
     */
    Optional<String> url();

    /**
     * Email of the contact
     */
    Optional<String> email();
}
