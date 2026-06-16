package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.common.annotation.NonBlocking;

@JsonRPCApi
public class VoidResource {

    private static volatile String lastMessage;

    public void fireAndForget() {
        lastMessage = "fired";
    }

    public void fireAndForget(String message) {
        lastMessage = message;
    }

    @NonBlocking
    public void fireAndForgetNonBlocking() {
        lastMessage = "fired-nb";
    }

    public static String getLastMessage() {
        return lastMessage;
    }

    public static void resetLastMessage() {
        lastMessage = null;
    }
}
