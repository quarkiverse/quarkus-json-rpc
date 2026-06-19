package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi(path = "no-leading-slash")
public class InvalidPathResource {

    public String hello() {
        return "should not work";
    }
}
