package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;

@JsonRPCApi
public class RunOnVirtualThreadMultiResource {

    @RunOnVirtualThread
    public Multi<String> stream() {
        return Multi.createFrom().items("a", "b", "c");
    }
}
