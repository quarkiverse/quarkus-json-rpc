package io.quarkiverse.jsonrpc.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.arc.Arc;

class JsonRPCMetrics implements JsonRPCMetricsHandler {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Timer> successTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> errorTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> subscriptionErrorCounters = new ConcurrentHashMap<>();
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong activeSubscriptions = new AtomicLong(0);

    private JsonRPCMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("jsonrpc.active.connections", activeConnections, AtomicLong::doubleValue)
                .description("Current number of active JSON-RPC WebSocket connections")
                .register(registry);
        Gauge.builder("jsonrpc.subscriptions.active", activeSubscriptions, AtomicLong::doubleValue)
                .description("Current number of active JSON-RPC streaming subscriptions")
                .register(registry);
    }

    static JsonRPCMetrics create() {
        MeterRegistry registry = Arc.container().select(MeterRegistry.class).get();
        return new JsonRPCMetrics(registry);
    }

    @Override
    public void recordSuccess(String method, long durationNanos) {
        getTimer(method, "success", successTimers).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordError(String method, long durationNanos) {
        getTimer(method, "error", errorTimers).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    @Override
    public void connectionClosed() {
        activeConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    @Override
    public void subscriptionStarted() {
        activeSubscriptions.incrementAndGet();
    }

    @Override
    public void subscriptionEnded() {
        activeSubscriptions.updateAndGet(v -> Math.max(0, v - 1));
    }

    @Override
    public void recordSubscriptionError(String method) {
        subscriptionErrorCounters.computeIfAbsent(method, m -> Counter.builder("jsonrpc.subscription.errors")
                .description("Number of errors during active JSON-RPC streaming subscriptions")
                .tag("method", m)
                .register(registry))
                .increment();
    }

    private Timer getTimer(String method, String outcome, ConcurrentHashMap<String, Timer> cache) {
        return cache.computeIfAbsent(method, m -> Timer.builder("jsonrpc.requests")
                .description("JSON-RPC method invocation duration and count")
                .tag("method", m)
                .tag("outcome", outcome)
                .register(registry));
    }
}
