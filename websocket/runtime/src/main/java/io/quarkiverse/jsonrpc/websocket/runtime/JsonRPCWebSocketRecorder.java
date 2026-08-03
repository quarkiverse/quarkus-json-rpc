package io.quarkiverse.jsonrpc.websocket.runtime;

import java.util.Map;
import java.util.Set;

import io.quarkiverse.jsonrpc.runtime.JsonRPCRouter;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class JsonRPCWebSocketRecorder {

    public Handler<RoutingContext> webSocketHandler(BeanContainer beanContainer) {
        return new JsonRPCWebSocket(beanContainer.beanInstance(JsonRPCRouter.class));
    }

    public Handler<RoutingContext> subProtocolHandler(Set<String> wsPaths) {
        return new JsonRPCSubProtocolHandler(wsPaths);
    }

    public Handler<RoutingContext> openRpcHandler(String openrpcDocument) {
        return new OpenRPCHandler(openrpcDocument);
    }

    public void configurePathMapping(BeanContainer beanContainer, Map<String, String> scopeToPath,
            String defaultPath) {
        beanContainer.beanInstance(JsonRPCRouter.class).setPathMapping(scopeToPath, defaultPath);
    }

    public void enableMessageLog(BeanContainer beanContainer) {
        beanContainer.beanInstance(JsonRPCRouter.class).enableMessageLog();
    }
}
