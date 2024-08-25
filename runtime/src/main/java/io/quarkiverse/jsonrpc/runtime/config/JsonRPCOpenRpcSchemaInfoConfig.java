package io.quarkiverse.jsonrpc.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigGroup
public interface JsonRPCOpenRpcSchemaInfoConfig {

    /**
     * Application Title
     */
    @WithDefault("JsonRPC API")
    public String title();

    /**
     * Application Description
     */
    @WithDefault("A JsonRPC API")
    public String description();

    /**
     * Terms Of Service URL
     */
    @WithName("terms-of-service")
    public Optional<String> termsOfService();

    /**
     * Application Owner Contact Information
     */
    @WithName("contact")
    public Optional<JsonRPCOpenRpcSchemaContactConfig> contact();

    /**
     * API License
     */
    @WithName("license")
    public Optional<JsonRPCOpenRpcSchemaLicenseConfig> license();

    /**
     * Application version
     */
    @WithName("version")
    @WithDefault("0.0.0")
    public String version();
}
