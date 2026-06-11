package io.quarkiverse.jsonrpc.runtime;

interface JsonRPCMetricsHandler {

    void recordSuccess(String method, long durationNanos);

    void recordError(String method, long durationNanos);

    void connectionOpened();

    void connectionClosed();

    void subscriptionStarted();

    void subscriptionEnded();

    void recordSubscriptionError(String method);
}
