package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Multi;

@JsonRPCApi(path = "/custom-rpc")
public class CustomPathResource {

    public String customHello() {
        return "Hello from custom path [" + Thread.currentThread().getName() + "]";
    }

    public String customHello(String name) {
        return "Hello " + name + " from custom path [" + Thread.currentThread().getName() + "]";
    }

    public Multi<String> customStream() {
        return Multi.createFrom().items("custom-0", "custom-1", "custom-2");
    }
}
