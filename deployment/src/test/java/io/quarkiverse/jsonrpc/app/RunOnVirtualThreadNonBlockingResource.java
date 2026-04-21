package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;

@JsonRPCApi
public class RunOnVirtualThreadNonBlockingResource {

    @RunOnVirtualThread
    @NonBlocking
    public String conflict() {
        return "This should not be reachable";
    }
}
