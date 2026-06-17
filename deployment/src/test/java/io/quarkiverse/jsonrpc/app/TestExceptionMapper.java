package io.quarkiverse.jsonrpc.app;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jsonrpc.api.JsonRPCError;
import io.quarkiverse.jsonrpc.api.JsonRPCExceptionMapper;

@ApplicationScoped
public class TestExceptionMapper implements JsonRPCExceptionMapper {

    @Override
    public JsonRPCError mapException(Throwable exception) {
        if (exception instanceof MapperBrokenException) {
            throw new RuntimeException("mapper bug");
        }
        if (exception instanceof OrderNotFoundException e) {
            return new JsonRPCError(-40001, "Order not found",
                    Map.of("orderId", e.getOrderId()));
        }
        return null;
    }
}
