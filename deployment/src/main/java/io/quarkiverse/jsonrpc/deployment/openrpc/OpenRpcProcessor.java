package io.quarkiverse.jsonrpc.deployment.openrpc;

import io.quarkiverse.jsonrpc.deployment.config.JsonRpcConfig;

public class OpenRpcProcessor {

    private JsonRPCOpenRpc createDocument(JsonRpcConfig jsonRPCConfig) {
        JsonRPCOpenRpc document = JsonRPCOpenRpc.builder().build();
        return document;
    }
}
