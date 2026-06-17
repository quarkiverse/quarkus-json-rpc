package io.quarkiverse.jsonrpc.runtime.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

public class JsonRPCResponse<T> {

    public final JsonNode id;
    public final T result;
    public final Error error;

    public JsonRPCResponse(JsonNode id, T result) {
        this.id = id;
        this.result = result;
        this.error = null;
    }

    public JsonRPCResponse(JsonNode id, Error error) {
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
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public final Object data;

        public Error(int code, String message) {
            this(code, message, null);
        }

        public Error(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        @Override
        public String toString() {
            return "error {" +
                    "  code=" + code + "," +
                    "  message='" + message + "'" +
                    (data != null ? ",  data=" + data : "") +
                    "}";
        }
    }
}
