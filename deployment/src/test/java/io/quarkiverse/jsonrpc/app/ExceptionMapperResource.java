package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi
public class ExceptionMapperResource {

    public String orderLookup(String orderId) {
        throw new OrderNotFoundException(orderId);
    }

    public String unmappedException() {
        throw new IllegalStateException("something went wrong");
    }
}
