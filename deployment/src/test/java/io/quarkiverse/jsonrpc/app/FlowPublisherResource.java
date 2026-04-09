package io.quarkiverse.jsonrpc.app;

import java.util.concurrent.Flow;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Multi;

@JsonRPCApi
public class FlowPublisherResource {

    public Flow.Publisher<String> items() {
        return Multi.createFrom().items("fp-0", "fp-1", "fp-2");
    }

    public Flow.Publisher<String> items(String prefix) {
        return Multi.createFrom().items(prefix + "-0", prefix + "-1", prefix + "-2");
    }

    public Flow.Publisher<String> failing() {
        return Multi.createFrom().failure(new RuntimeException("Flow.Publisher test error"));
    }
}
