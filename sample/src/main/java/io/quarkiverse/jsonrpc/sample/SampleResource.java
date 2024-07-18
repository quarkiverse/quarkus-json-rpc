package io.quarkiverse.jsonrpc.sample;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;

@JsonRPCApi
public class SampleResource {

    public String hello() {
        return "Hello";
    }
}
