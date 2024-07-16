package io.quarkiverse.jsonrpc.runtime;

import java.util.Map;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class JsonRPCRecorder {

    public void createJsonRpcRouter(BeanContainer beanContainer,
            Map<String, Map<JsonRPCMethodName, JsonRPCMethod>> methodsMap) {
        JsonRPCRouter jsonRpcRouter = beanContainer.beanInstance(JsonRPCRouter.class);
        jsonRpcRouter.populateJsonRPCMethods(methodsMap);
    }

    public Handler<RoutingContext> webSocketHandler() {
        return new JsonRPCWebSocket();
    }
}
