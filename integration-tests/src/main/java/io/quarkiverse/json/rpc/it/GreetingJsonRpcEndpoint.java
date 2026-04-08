package io.quarkiverse.json.rpc.it;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Uni;

@JsonRPCApi
public class GreetingJsonRpcEndpoint {

    public String greet() {
        return "Hello from JSON-RPC";
    }

    public String greet(String name) {
        return "Hello " + name;
    }

    public Uni<String> greetAsync(String name) {
        return Uni.createFrom().item("Hello async " + name);
    }
}
