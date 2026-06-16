package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi
public class ConflictingVoidReturnTypeResource {

    public void process() {
    }

    public String process(String input) {
        return "processed: " + input;
    }
}
