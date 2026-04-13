package io.quarkiverse.jsonrpc.runtime;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jsonrpc.api.JsonRPCBroadcaster;
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

    public Supplier<JsonRPCSessions> createJsonRpcSessions() {
        return JsonRPCSessions::new;
    }

    public Function<SyntheticCreationalContext<JsonRPCCodec>, JsonRPCCodec> createJsonRpcCodec() {
        return new Function<>() {
            @Override
            public JsonRPCCodec apply(SyntheticCreationalContext<JsonRPCCodec> context) {
                return new JsonRPCCodec(context.getInjectedReference(ObjectMapper.class));
            }
        };
    }

    public Function<SyntheticCreationalContext<JsonRPCRouter>, JsonRPCRouter> createJsonRpcRouter(
            Map<JsonRPCMethodName, JsonRPCMethod> methodsMap) {
        return new Function<>() {
            @Override
            public JsonRPCRouter apply(SyntheticCreationalContext<JsonRPCRouter> context) {
                return new JsonRPCRouter(
                        context.getInjectedReference(JsonRPCCodec.class),
                        context.getInjectedReference(JsonRPCSessions.class),
                        methodsMap);
            }
        };
    }

    public Function<SyntheticCreationalContext<JsonRPCBroadcaster>, JsonRPCBroadcaster> createJsonRpcBroadcaster() {
        return new Function<>() {
            @Override
            public JsonRPCBroadcaster apply(SyntheticCreationalContext<JsonRPCBroadcaster> context) {
                return new JsonRPCBroadcaster(
                        context.getInjectedReference(JsonRPCCodec.class),
                        context.getInjectedReference(JsonRPCSessions.class));
            }
        };
    }

    public Handler<RoutingContext> webSocketHandler(BeanContainer beanContainer) {
        return new JsonRPCWebSocket(beanContainer.beanInstance(JsonRPCRouter.class));
    }

    public Handler<RoutingContext> subProtocolHandler(String wsPath) {
        return new JsonRPCSubProtocolHandler(wsPath);
    }
}
