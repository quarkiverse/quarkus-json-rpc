package io.quarkiverse.jsonrpc.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class JsonRPCMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Timer> successTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> errorTimers = new ConcurrentHashMap<>();
    private final AtomicLong activeConnections = new AtomicLong(0);

    JsonRPCMetrics(MeterRegistry registry) {
        this.registry = registry;
        io.micrometer.core.instrument.Gauge.builder("jsonrpc.active.connections", activeConnections, AtomicLong::doubleValue)
                .description("Current number of active JSON-RPC WebSocket connections")
                .register(registry);
    }

    void recordSuccess(String method, long durationNanos) {
        getTimer(method, "success", successTimers).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    void recordError(String method, long durationNanos) {
        getTimer(method, "error", errorTimers).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    void connectionClosed() {
        activeConnections.decrementAndGet();
    }

    private Timer getTimer(String method, String outcome, ConcurrentHashMap<String, Timer> cache) {
        return cache.computeIfAbsent(method, m -> Timer.builder("jsonrpc.requests")
                .description("JSON-RPC method invocation duration and count")
                .tag("method", m)
                .tag("outcome", outcome)
                .register(registry));
    }
}
