package io.quarkiverse.jsonrpc.runtime;

import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCCodec;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class JsonRPCRecorder {

    public Function<SyntheticCreationalContext<JsonRPCRouter>, JsonRPCRouter> createJsonRpcRouter(
            Map<JsonRPCMethodName, JsonRPCMethod> methodsMap) {
        return new Function<>() {
            @Override
            public JsonRPCRouter apply(SyntheticCreationalContext<JsonRPCRouter> context) {
                return new JsonRPCRouter(new JsonRPCCodec(context.getInjectedReference(ObjectMapper.class)), methodsMap);
            }
        };
    }

    public Function<SyntheticCreationalContext<JsonRPCWebSocket>, JsonRPCWebSocket> createJsonRpcWebSocket() {
        return new Function<>() {
            @Override
            public JsonRPCWebSocket apply(SyntheticCreationalContext<JsonRPCWebSocket> context) {
                return new JsonRPCWebSocket(context.getInjectedReference(JsonRPCRouter.class));
            }
        };
    }

    public Handler<RoutingContext> webSocketHandler(BeanContainer beanContainer) {
        return new JsonRPCWebSocket(beanContainer.beanInstance(JsonRPCRouter.class));
    }
}
