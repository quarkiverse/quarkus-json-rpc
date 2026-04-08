package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;

@JsonRPCApi
public class BlockingNonBlockingResource {

    @Blocking
    @NonBlocking
    public String conflict() {
        return "This should not be reachable";
    }
}
