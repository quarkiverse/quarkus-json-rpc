package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Multi;

@JsonRPCApi
public class MultiResource {

    public Multi<String> items() {
        return Multi.createFrom().items("item-0", "item-1", "item-2");
    }

    public Multi<String> items(String prefix) {
        return Multi.createFrom().items(prefix + "-0", prefix + "-1", prefix + "-2");
    }

    public Multi<String> failing() {
        return Multi.createFrom().failure(new RuntimeException("Multi test error"));
    }
}
