package io.quarkiverse.jsonrpc.sample;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;

@JsonRPCApi
public class HelloResource {

    public String hello() {
        return "Hello";
    }
}
