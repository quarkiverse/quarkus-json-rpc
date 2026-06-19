package io.quarkiverse.jsonrpc.sample;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@JsonRPCApi(path = "/custom-rpc")
public class CustomPathResource {

    public String hello() {
        return "Hello from custom path [" + Thread.currentThread().getName() + "]";
    }

    public String hello(String name) {
        return "Hello " + name + " from custom path [" + Thread.currentThread().getName() + "]";
    }

    public Uni<String> helloUni() {
        return Uni.createFrom().item(hello());
    }

    public Multi<String> helloMulti() {
        return Multi.createFrom().items("custom-0", "custom-1", "custom-2");
    }
}
