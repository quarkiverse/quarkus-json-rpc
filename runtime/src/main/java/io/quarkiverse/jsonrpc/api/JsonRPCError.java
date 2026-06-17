package io.quarkiverse.jsonrpc.api;

public record JsonRPCError(int code, String message, Object data) {

    public JsonRPCError(int code, String message) {
        this(code, message, null);
    }
}
