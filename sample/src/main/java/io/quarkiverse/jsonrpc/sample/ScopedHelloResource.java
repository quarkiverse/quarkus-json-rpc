package io.quarkiverse.jsonrpc.sample;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;

@JsonRPCApi("scoped")
public class ScopedHelloResource {

    public String hello() {
        return "Hello scoped";
    }
}
