package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi(path = "/custom-rpc")
public class CustomPathResource {

    public String customHello() {
        return "Hello from custom path [" + Thread.currentThread().getName() + "]";
    }

    public String customHello(String name) {
        return "Hello " + name + " from custom path [" + Thread.currentThread().getName() + "]";
    }
}
