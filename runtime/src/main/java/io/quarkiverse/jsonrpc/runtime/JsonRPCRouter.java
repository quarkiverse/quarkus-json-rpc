package io.quarkiverse.jsonrpc.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCCodec;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMessage;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCRequest;
import io.quarkiverse.jsonrpc.runtime.model.MessageType;
import io.quarkus.arc.Arc;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.http.ServerWebSocket;

/**
 * Route JsonRPC message to the correct method
 */
public class JsonRPCRouter {
    private static final Logger LOG = Logger.getLogger(JsonRPCRouter.class.getName());
    private final Map<Integer, Cancellable> subscriptions = new ConcurrentHashMap<>();

    // Map json-rpc method to java in runtime classpath
    private final Map<String, ReflectionInfo> jsonRpcToJava = new HashMap<>();

    private static final List<ServerWebSocket> SESSIONS = Collections.synchronizedList(new ArrayList<>());
    private JsonRPCCodec codec = new JsonRPCCodec();

    /**
     * This gets called on build to build into of the classes we are going to call in runtime
     *
     * @param methodsMap
     */
    public void populateJsonRPCMethods(Map<JsonRPCMethodName, JsonRPCMethod> methodsMap) {
        for (Map.Entry<JsonRPCMethodName, JsonRPCMethod> method : methodsMap.entrySet()) {
            JsonRPCMethodName methodName = method.getKey();
            JsonRPCMethod jsonRpcMethod = method.getValue();

            @SuppressWarnings("unchecked")
            Object providerInstance = Arc.container().select(jsonRpcMethod.getClazz()).get();

            try {
                Method javaMethod;
                Map<String, Class> params = null;
                if (jsonRpcMethod.hasParams()) {
                    params = jsonRpcMethod.getParams();
                    javaMethod = providerInstance.getClass().getMethod(jsonRpcMethod.getMethodName(),
                            params.values().toArray(new Class[] {}));
                } else {
                    javaMethod = providerInstance.getClass().getMethod(jsonRpcMethod.getMethodName());
                }
                ReflectionInfo reflectionInfo = new ReflectionInfo(jsonRpcMethod.getClazz(), providerInstance, javaMethod,
                        params, jsonRpcMethod.getExplicitlyBlocking(), jsonRpcMethod.getExplicitlyNonBlocking());
                jsonRpcToJava.put(methodName.toString(), reflectionInfo);
            } catch (NoSuchMethodException | SecurityException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private Uni<?> invoke(ReflectionInfo info, Object target, Object[] args) {
        if (info.isReturningUni()) {
            try {
                Uni<?> uni = ((Uni<?>) info.method.invoke(target, args));
                if (info.isExplicitlyBlocking()) {
                    return uni.runSubscriptionOn(Infrastructure.getDefaultExecutor());
                } else {
                    return uni;
                }
            } catch (Throwable e) {
                return Uni.createFrom().failure(e);
            }
        } else {
            Uni<?> uni = Uni.createFrom().item(Unchecked.supplier(() -> info.method.invoke(target, args)));
            if (!info.isExplicitlyNonBlocking()) {
                return uni.runSubscriptionOn(Infrastructure.getDefaultExecutor());
            } else {
                return uni;
            }
        }
    }

    public void addSocket(ServerWebSocket socket) {
        SESSIONS.add(socket);
        socket.textMessageHandler((e) -> {
            JsonRPCRequest jsonRpcRequest = codec.readRequest(e);
            route(jsonRpcRequest, socket);
        }).closeHandler((e) -> {
            purge();
        });
        purge();
    }

    private void purge() {
        for (ServerWebSocket s : new ArrayList<>(SESSIONS)) {
            if (s.isClosed()) {
                SESSIONS.remove(s);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void route(JsonRPCRequest jsonRpcRequest, ServerWebSocket s) {
        String jsonRpcMethodName = jsonRpcRequest.getMethod();

        // First check some internal methods
        if (jsonRpcMethodName.equalsIgnoreCase(UNSUBSCRIBE)) {
            if (this.subscriptions.containsKey(jsonRpcRequest.getId())) {
                Cancellable cancellable = this.subscriptions.remove(jsonRpcRequest.getId());
                cancellable.cancel();
            }
            codec.writeResponse(s, jsonRpcRequest.getId(), null, MessageType.Void);
        } else if (this.jsonRpcToJava.containsKey(jsonRpcMethodName)) { // Route to extension (runtime)
            ReflectionInfo reflectionInfo = this.jsonRpcToJava.get(jsonRpcMethodName);
            Object target = Arc.container().select(reflectionInfo.bean).get();

            if (reflectionInfo.isReturningMulti()) {
                Multi<?> multi;
                try {
                    if (jsonRpcRequest.hasParams()) {
                        Object[] args = getArgsAsObjects(reflectionInfo.params, jsonRpcRequest);
                        multi = (Multi<?>) reflectionInfo.method.invoke(target, args);
                    } else {
                        multi = (Multi<?>) reflectionInfo.method.invoke(target);
                    }
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to invoke method %s using JSON-RPC, request was: %s", jsonRpcMethodName,
                            jsonRpcRequest);
                    codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcMethodName, e);
                    return;
                }

                Cancellable cancellable = multi.subscribe()
                        .with(
                                item -> {
                                    codec.writeResponse(s, jsonRpcRequest.getId(), item, MessageType.SubscriptionMessage);
                                },
                                failure -> {
                                    codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcMethodName, failure);
                                    this.subscriptions.remove(jsonRpcRequest.getId());
                                },
                                () -> this.subscriptions.remove(jsonRpcRequest.getId()));

                this.subscriptions.put(jsonRpcRequest.getId(), cancellable);
                codec.writeResponse(s, jsonRpcRequest.getId(), null, MessageType.Void);
            } else {
                // The invocation will return a Uni<JsonObject>
                Uni<?> uni;
                try {
                    if (jsonRpcRequest.hasParams()) {
                        Object[] args = getArgsAsObjects(reflectionInfo.params, jsonRpcRequest);
                        uni = invoke(reflectionInfo, target, args);
                    } else {
                        uni = invoke(reflectionInfo, target, new Object[0]);
                    }
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to invoke method %s using JSON-RPC, request was: %s", jsonRpcMethodName,
                            jsonRpcRequest);
                    codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcMethodName, e);
                    return;
                }
                uni.subscribe()
                        .with(item -> {
                            if (item != null && JsonRPCMessage.class.isAssignableFrom(item.getClass())) {
                                JsonRPCMessage jsonRpcMessage = (JsonRPCMessage) item;
                                codec.writeResponse(s, jsonRpcRequest.getId(), jsonRpcMessage.getResponse(),
                                        jsonRpcMessage.getMessageType());
                            } else {
                                codec.writeResponse(s, jsonRpcRequest.getId(), item,
                                        MessageType.Response);
                            }
                        }, failure -> {
                            Throwable actualFailure;
                            // If the jsonrpc method is actually
                            // synchronous, the failure is wrapped in an
                            // InvocationTargetException, so unwrap it here
                            if (failure instanceof InvocationTargetException f) {
                                actualFailure = f.getTargetException();
                            } else if (failure.getCause() != null
                                    && failure.getCause() instanceof InvocationTargetException f) {
                                actualFailure = f.getTargetException();
                            } else {
                                actualFailure = failure;
                            }
                            codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcMethodName, actualFailure);
                        });
            }
        } else {
            // Method not found
            codec.writeMethodNotFoundResponse(s, jsonRpcRequest.getId(), jsonRpcMethodName);
        }
    }

    private Object[] getArgsAsObjects(Map<String, Class> params, JsonRPCRequest jsonRpcRequest) {
        List<Object> objects = new ArrayList<>();
        for (Map.Entry<String, Class> expectedParams : params.entrySet()) {
            String paramName = expectedParams.getKey();
            Class paramType = expectedParams.getValue();
            Object param = jsonRpcRequest.getParam(paramName, paramType);
            objects.add(param);
        }
        return objects.toArray(Object[]::new);
    }

    private static final String DOT = ".";
    private static final String UNSUBSCRIBE = "unsubscribe";
}
