package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Multi;

@JsonRPCApi
public class ExceptionMapperResource {

    public String orderLookup(String orderId) {
        throw new OrderNotFoundException(orderId);
    }

    public String unmappedException() {
        throw new IllegalStateException("something went wrong");
    }

    public String mapperThrows() {
        throw new MapperBrokenException("trigger broken mapper");
    }

    public Multi<String> failingStream(String orderId) {
        return Multi.createFrom().failure(new OrderNotFoundException(orderId));
    }
}
