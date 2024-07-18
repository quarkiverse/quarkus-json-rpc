package io.quarkiverse.jsonrpc.runtime.model;

public class JsonRPCResponse<T> {

    // Public for serialization
    public final int id;
    public final T result;
    public final Error error;

    public JsonRPCResponse(int id, T result) {
        this.id = id;
        this.result = result;
        this.error = null;
    }

    public JsonRPCResponse(int id, Error error) {
        this.id = id;
        this.result = null;
        this.error = error;
    }

    public String getJsonrpc() {
        return JsonRPCKeys.VERSION;
    }

    @Override
    public String toString() {
        return "jsonRpcResponse{" +
                "  id=" + id + "," +
                "  result=" + result + "," +
                "  error=" + error +
                "}";
    }

    public static final class Error {
        public final int code;
        public final String message;

        public Error(int code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String toString() {
            return "error {" +
                    "  code=" + code + "," +
                    "  message='" + message + "'" +
                    "}";
        }
    }
}
