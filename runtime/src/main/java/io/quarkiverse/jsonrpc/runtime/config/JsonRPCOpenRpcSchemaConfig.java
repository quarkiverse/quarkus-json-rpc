package io.quarkiverse.jsonrpc.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigGroup
public interface JsonRPCOpenRpcSchemaConfig {

    /**
     * OpenRPC Schema Spec version
     */
    @WithDefault("1.2.6")
    public String version();

    /**
     * OpenRPC Schema Application Info
     */
    @WithName("info")
    public JsonRPCOpenRpcSchemaInfoConfig info();

}
