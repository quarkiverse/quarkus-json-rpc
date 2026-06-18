package io.quarkiverse.jsonrpc.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.json-rpc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface JsonRPCRuntimeConfig {

    /**
     * Global timeout for JSON-RPC method execution.
     * If a method does not complete within this duration, a timeout error is returned to the client.
     * Applies to all non-streaming methods (not Multi or Flow.Publisher).
     * Per-method timeouts can be configured using MicroProfile Fault Tolerance's {@code @Timeout} annotation
     * when {@code quarkus-smallrye-fault-tolerance} is on the classpath.
     */
    Optional<Duration> methodTimeout();
}
