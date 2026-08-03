package io.quarkiverse.jsonrpc.domainsocket.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * JsonRPC Domain Socket Configuration
 */
@ConfigMapping(prefix = "quarkus.json-rpc")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface JsonRPCDomainSocketBuildConfig {

    /**
     * Configuration properties for the Unix domain socket transport (JSONL framing)
     */
    @WithName("domain-socket")
    JsonRPCDomainSocketConfig domainSocket();
}
