package io.quarkiverse.jsonrpc.runtime.devui;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import jakarta.inject.Inject;

import io.quarkiverse.jsonrpc.runtime.JsonRPCRouter;
import io.quarkiverse.jsonrpc.runtime.JsonRPCSessions;
import io.quarkiverse.jsonrpc.runtime.ReflectionInfo;
import io.quarkus.runtime.annotations.DevMCPEnableByDefault;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Multi;
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
        Map<String, String> methodPaths = router.getMethodPaths();
        for (Map.Entry<String, ReflectionInfo> entry : router.getRegisteredMethods().entrySet()) {
            String key = entry.getKey();
            ReflectionInfo info = entry.getValue();
            JsonObject method = new JsonObject();
            method.put("key", key);
            method.put("className", info.bean.getSimpleName());
            method.put("methodName", info.method.getName());
            method.put("path", methodPaths.getOrDefault(key, ""));

            if (info.params != null && !info.params.isEmpty()) {
                StringJoiner sj = new StringJoiner(", ");
                for (Map.Entry<String, Class> param : info.params.entrySet()) {
                    sj.add(param.getKey() + ": " + param.getValue().getSimpleName());
                }
                method.put("parameters", sj.toString());
            } else {
                method.put("parameters", "");
            }
            method.put("executionMode", getExecutionMode(info));
            method.put("security", resolveSecurityConstraint(info));

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

    public Multi<JsonObject> streamLog() {
        return router.streamMessageLog();
    }

    private String resolveSecurityConstraint(ReflectionInfo info) {
        Method method = info.method;

        String label = getSecurityLabel(method.getAnnotations());
        if (label != null) {
            return label;
        }

        label = getSecurityLabel(info.bean.getAnnotations());
        if (label != null) {
            return label;
        }

        return "";
    }

    private String getSecurityLabel(Annotation[] annotations) {
        for (Annotation ann : annotations) {
            String name = ann.annotationType().getSimpleName();
            switch (name) {
                case "RolesAllowed":
                    try {
                        String[] roles = (String[]) ann.annotationType().getMethod("value").invoke(ann);
                        return "@RolesAllowed(" + String.join(", ", roles) + ")";
                    } catch (Exception e) {
                        return "@RolesAllowed";
                    }
                case "PermitAll":
                    return "@PermitAll";
                case "DenyAll":
                    return "@DenyAll";
                case "Authenticated":
                    return "@Authenticated";
            }
        }
        return null;
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
