package io.quarkiverse.jsonrpc.runtime.config;

import java.nio.file.Path;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigGroup
public interface JsonRPCOpenRpcConfig {
    /**
     * Enable JsonRPC OpenRPC Spec
     */
    @WithDefault("true")
    public boolean enabled();

    /**
     * HTTP Path for the JsonRPC OpenRPC DevUI
     */
    @WithDefault("open-rpc")
    public String basePath();

    /**
     * HTTP Path for the JsonRPC OpenRPC Playground
     */
    @WithDefault("playground")
    public String playgroundPath();

    /**
     * HTTP Path for the JsonRPC OpenRPC Spec file
     */
    @WithDefault("openrpc.json")
    public String schemaPath();

    /**
     * If the OpenRPC Schema file should be accessible
     */
    @WithDefault("true")
    public boolean schemaAvailable();

    /**
     * Location to store the generated OpenRPC Schema file
     */
    @WithDefault("/open-rpc")
    public Path storeSchemaDirectory();

    /**
     * OpenRPC Schema Config
     *
     * @return OpenRPC Schema Config
     */
    @WithName("schema")
    public JsonRPCOpenRpcSchemaConfig schema();

}
