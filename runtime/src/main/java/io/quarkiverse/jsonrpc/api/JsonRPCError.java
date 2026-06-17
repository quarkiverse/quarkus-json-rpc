package io.quarkiverse.jsonrpc.api;

public class JsonRPCError {

    private final int code;
    private final String message;
    private final Object data;

    public JsonRPCError(int code, String message) {
        this(code, message, null);
    }

    public JsonRPCError(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
