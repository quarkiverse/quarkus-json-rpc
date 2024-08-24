package io.quarkiverse.jsonrpc.runtime.config;

import java.nio.file.Path;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class JsonRPCOpenRpcConfig {
    /**
     * Enable JsonRPC OpenRPC Spec
     */
    @ConfigItem(defaultValue = "true")
    public boolean enabled;

    /**
     * HTTP Path for the JsonRPC OpenRPC DevUI
     */
    @ConfigItem(defaultValue = "open-rpc")
    public String basePath;

    /**
     * HTTP Path for the JsonRPC OpenRPC Playground
     */
    @ConfigItem(defaultValue = "playground")
    public String playgroundPath;

    /**
     * HTTP Path for the JsonRPC OpenRPC Spec file
     */
    @ConfigItem(defaultValue = "openrpc.json")
    public String schemaPath;

    /**
     * If the OpenRPC Schema file should be accessible
     */
    @ConfigItem(defaultValue = "true")
    public boolean schemaAvailable;

    /**
     * Location to store the generated OpenRPC Schema file
     */
    @ConfigItem(defaultValue = "/open-rpc")
    public Path storeSchemaDirectory;
}
