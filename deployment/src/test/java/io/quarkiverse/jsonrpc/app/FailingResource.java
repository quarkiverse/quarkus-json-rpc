package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi
public class FailingResource {

    public String fail() {
        throw new RuntimeException("expected test error");
    }
}
