package io.quarkiverse.jsonrpc.app;

import java.time.Duration;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Multi;

/**
 * Test resource with overloaded methods that have conflicting return types:
 * one returns a plain value (call) and one returns Multi (subscribe).
 * This should cause a build-time error when the JS client is enabled.
 */
@JsonRPCApi
public class ConflictingReturnTypeResource {

    public String data() {
        return "plain";
    }

    public Multi<String> data(String filter) {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(n -> "item " + n);
    }
}
