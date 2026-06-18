package io.quarkiverse.jsonrpc.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class JsonRPCHealthCheck implements HealthCheck {

    @Inject
    JsonRPCSessions sessions;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("JSON-RPC WebSocket").up();
        builder.withData("activeConnections", sessions.getAllSockets().size());
        return builder.build();
    }
}
