package io.quarkiverse.jsonrpc.deployment.config;

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
     * HTTP Path for the JsonRPC OpenRPC Playground
     */
    @ConfigItem(defaultValue = "/open-rpc/playground")
    public String playgroundPath;

    /**
     * HTTP Path for the JsonRPC OpenRPC Spec file
     */
    @ConfigItem(defaultValue = "/open-rpc/openrpc.json")
    public String specPath;

    /**
     * If the OpenRPC Schema file should be accessible
     */
    @ConfigItem(defaultValue = "true")
    public boolean schemaAvailable;
}
