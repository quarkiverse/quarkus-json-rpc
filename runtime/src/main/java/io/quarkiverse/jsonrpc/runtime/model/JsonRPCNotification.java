package io.quarkiverse.jsonrpc.runtime.model;

import java.util.Map;

/**
 * A JSON-RPC 2.0 notification (no id field).
 * Used for subscription item delivery, completion, and error signals.
 */
public class JsonRPCNotification {

    public final String method;
    public final Map<String, Object> params;

    public JsonRPCNotification(String method, Map<String, Object> params) {
        this.method = method;
        this.params = params;
    }

    public String getJsonrpc() {
        return JsonRPCKeys.VERSION;
    }
}
