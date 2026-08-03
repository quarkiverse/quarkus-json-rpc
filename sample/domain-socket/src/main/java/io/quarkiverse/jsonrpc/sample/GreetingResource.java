package io.quarkiverse.jsonrpc.sample;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@JsonRPCApi
public class GreetingResource {

    public String greet() {
        return "Hello from JSON-RPC over domain socket";
    }

    public String greet(String name) {
        return "Hello " + name;
    }

    public Uni<String> greetAsync(String name) {
        return Uni.createFrom().item("Hello async " + name);
    }

    public Multi<String> greetStream(String name) {
        return Multi.createFrom().items("Hello " + name + " (1)", "Hello " + name + " (2)", "Hello " + name + " (3)");
    }
}
