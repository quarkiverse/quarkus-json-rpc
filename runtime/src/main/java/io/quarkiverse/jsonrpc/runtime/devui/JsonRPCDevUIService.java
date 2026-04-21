package io.quarkiverse.jsonrpc.runtime.devui;

import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import io.quarkiverse.jsonrpc.runtime.JsonRPCRouter;
import io.quarkiverse.jsonrpc.runtime.JsonRPCSessions;
import io.quarkiverse.jsonrpc.runtime.ReflectionInfo;
import io.quarkus.runtime.annotations.DevMCPEnableByDefault;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonRPCDevUIService {

    @Inject
    JsonRPCRouter router;

    @Inject
    JsonRPCSessions sessions;

    @NonBlocking
    @JsonRpcDescription("List all registered JSON-RPC methods with their scope, name, parameters, return type, and execution mode")
    @DevMCPEnableByDefault
    public JsonArray listMethods() {
        JsonArray methods = new JsonArray();
        for (Map.Entry<String, ReflectionInfo> entry : router.getRegisteredMethods().entrySet()) {
            String key = entry.getKey();
            ReflectionInfo info = entry.getValue();
            JsonObject method = new JsonObject();
            method.put("key", key);
            method.put("className", info.bean.getName());
            method.put("methodName", info.method.getName());
            method.put("returnType", getReturnTypeDescription(info));

            JsonArray params = new JsonArray();
            if (info.params != null) {
                for (Map.Entry<String, Class> param : info.params.entrySet()) {
                    params.add(new JsonObject()
                            .put("name", param.getKey())
                            .put("type", param.getValue().getSimpleName()));
                }
            }
            method.put("parameters", params);
            method.put("executionMode", getExecutionMode(info));

            methods.add(method);
        }
        return methods;
    }

    @NonBlocking
    @JsonRpcDescription("List all active WebSocket sessions connected to the JSON-RPC endpoint")
    @DevMCPEnableByDefault
    public JsonArray listSessions() {
        JsonArray result = new JsonArray();
        Set<String> sessionIds = sessions.getSessionIds();
        for (String id : sessionIds) {
            JsonObject session = new JsonObject();
            session.put("sessionId", id);

            // Count subscriptions for this session
            ServerWebSocket socket = sessions.getSocket(id);
            Map<ServerWebSocket, Map<String, Cancellable>> allSubs = router.getSocketSubscriptions();
            Map<String, Cancellable> subs = allSubs.get(socket);
            session.put("subscriptionCount", subs != null ? subs.size() : 0);

            result.add(session);
        }
        return result;
    }

    @NonBlocking
    @JsonRpcDescription("List all active streaming subscriptions across all connected sessions")
    @DevMCPEnableByDefault
    public JsonArray listSubscriptions() {
        JsonArray result = new JsonArray();
        Map<ServerWebSocket, Map<String, Cancellable>> allSubs = router.getSocketSubscriptions();
        for (Map.Entry<ServerWebSocket, Map<String, Cancellable>> entry : allSubs.entrySet()) {
            ServerWebSocket socket = entry.getKey();
            String sessionId = sessions.getSessionId(socket);
            for (String subId : entry.getValue().keySet()) {
                result.add(new JsonObject()
                        .put("sessionId", sessionId)
                        .put("subscriptionId", subId));
            }
        }
        return result;
    }

    private String getReturnTypeDescription(ReflectionInfo info) {
        Class<?> returnType = info.method.getReturnType();
        java.lang.reflect.Type genericReturn = info.method.getGenericReturnType();

        if (info.isReturningMulti()) {
            return "Multi<" + getGenericArg(genericReturn) + ">";
        } else if (info.isReturningUni()) {
            return "Uni<" + getGenericArg(genericReturn) + ">";
        } else if (info.isReturningCompletionStage()) {
            return "CompletionStage<" + getGenericArg(genericReturn) + ">";
        } else if (info.isReturningFlowPublisher()) {
            return "Flow.Publisher<" + getGenericArg(genericReturn) + ">";
        }
        return returnType.getSimpleName();
    }

    private String getGenericArg(java.lang.reflect.Type type) {
        if (type instanceof java.lang.reflect.ParameterizedType pt) {
            java.lang.reflect.Type[] args = pt.getActualTypeArguments();
            if (args.length > 0) {
                if (args[0] instanceof Class<?> c) {
                    return c.getSimpleName();
                }
                return args[0].getTypeName();
            }
        }
        return "?";
    }

    private String getExecutionMode(ReflectionInfo info) {
        switch (info.getExecutionMode()) {
            case BLOCKING:
                return "blocking";
            case NON_BLOCKING:
                return "non-blocking";
            case VIRTUAL_THREAD:
                return "virtual thread";
            default:
                if (info.isReturningUni() || info.isReturningMulti()
                        || info.isReturningCompletionStage() || info.isReturningFlowPublisher()) {
                    return "non-blocking (default)";
                }
                return "blocking (default)";
        }
    }
}
