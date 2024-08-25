package io.quarkiverse.jsonrpc.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface JsonRPCOpenRpcSchemaLicenseConfig {

    /**
     * Name of License
     */
    public Optional<String> name();

    /**
     * URL of the License
     */
    public Optional<String> url();
}
