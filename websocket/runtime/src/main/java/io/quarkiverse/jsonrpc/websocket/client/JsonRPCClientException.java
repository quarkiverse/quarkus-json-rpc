package io.quarkiverse.jsonrpc.websocket.client;

/**
 * Thrown (or emitted as failure) when the server answers a call or subscription with a JSON-RPC error.
 */
public class JsonRPCClientException extends RuntimeException {

    private final int code;
    private final Object data;

    public JsonRPCClientException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
