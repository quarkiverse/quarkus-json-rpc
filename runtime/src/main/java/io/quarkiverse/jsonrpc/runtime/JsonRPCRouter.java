package io.quarkiverse.jsonrpc.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCCodec;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCRequest;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.smallrye.context.SmallRyeThreadContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;

/**
 * Route JsonRPC message to the correct method
 */
public class JsonRPCRouter {
    private static final Logger LOG = Logger.getLogger(JsonRPCRouter.class.getName());
    private final Map<Integer, Cancellable> subscriptions = new ConcurrentHashMap<>();

    // Map json-rpc method to java in runtime classpath
    private final Map<String, ReflectionInfo> jsonRpcToJava = new HashMap<>();

    private final Map<String, String> orderedParameterKeyToNamedKey = new HashMap<>();

    private static final List<ServerWebSocket> SESSIONS = Collections.synchronizedList(new ArrayList<>());
    private JsonRPCCodec codec = new JsonRPCCodec();

    private ManagedContext currentManagedContext;

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
                if (methodName.hasOrderedParameterKey()) {
                    orderedParameterKeyToNamedKey.put(methodName.getOrderedParameterKey(), methodName.toString());
                }
            } catch (NoSuchMethodException | SecurityException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @PostConstruct
    public void init() {
        this.currentManagedContext = Arc.container().requestContext();
    }

    @PreDestroy
    public void destroy() {
        currentManagedContext.terminate();
    }

    @SuppressWarnings("unchecked")
    private Uni<?> invoke(ReflectionInfo info, Object target, Object[] args) {
        Context vc = Vertx.currentContext();

        try {
            if (!currentManagedContext.isActive()) {
                currentManagedContext.activate();
            }
            if (info.isReturningUni()) {
                if (info.isExplicitlyBlocking()) {
                    SmallRyeThreadContext threadContext = Arc.container().select(SmallRyeThreadContext.class).get();
                    final Promise<?> result = Promise.promise();
                    // We need some make sure that we call given the context
                    Callable<Object> contextualCallable = threadContext.contextualCallable(() -> {
                        Object resultFromMethodCall = info.method.invoke(target, args);
                        Uni<?> uniFromMethodCall = (Uni<?>) resultFromMethodCall;
                        return uniFromMethodCall.subscribeAsCompletionStage().get();
                    });
                    vc.executeBlocking(contextualCallable).onComplete((Handler<AsyncResult<Object>>) result);
                    return Uni.createFrom().completionStage(result.future().toCompletionStage());
                } else {
                    return (Uni<?>) info.method.invoke(target, args);
                }
            } else {
                if (info.isExplicitlyNonBlocking()) {
                    return Uni.createFrom().item(Unchecked.supplier(() -> info.method.invoke(target, args)));
                } else {
                    SmallRyeThreadContext threadContext = Arc.container().select(SmallRyeThreadContext.class).get();
                    final Promise<?> result = Promise.promise();
                    // We need some make sure that we call given the context
                    Callable<Object> contextualCallable = threadContext.contextualCallable(() -> {
                        return info.method.invoke(target, args);
                    });
                    vc.executeBlocking(contextualCallable).onComplete((Handler<AsyncResult<Object>>) result);
                    return Uni.createFrom().completionStage(result.future().toCompletionStage());
                }
            }
        } catch (Throwable e) {
            return Uni.createFrom().failure(e);
        } finally {
            currentManagedContext.deactivate();
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
        String key = Keys.createKey(jsonRpcRequest);
        if (jsonRpcRequest.hasPositionedParams()) {
            String opKey = Keys.createOrderedParameterKey(jsonRpcRequest);
            if (orderedParameterKeyToNamedKey.containsKey(opKey)) {
                key = orderedParameterKeyToNamedKey.get(opKey);
            }
        }

        if (this.jsonRpcToJava.containsKey(key)) {
            ReflectionInfo reflectionInfo = this.jsonRpcToJava.get(key);
            Object target = Arc.container().select(reflectionInfo.bean).get();

            if (reflectionInfo.isReturningMulti()) {
                Multi<?> multi;
                try {
                    if (jsonRpcRequest.hasNamedParams()) {
                        Object[] args = getArgsAsObjects(reflectionInfo.params, jsonRpcRequest);
                        multi = (Multi<?>) reflectionInfo.method.invoke(target, args);
                    } else {
                        multi = (Multi<?>) reflectionInfo.method.invoke(target);
                    }
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to invoke method %s using JSON-RPC, request was: %s", jsonRpcRequest.getMethod(),
                            jsonRpcRequest);
                    codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod(), e);
                    return;
                }

                Cancellable cancellable = multi.subscribe()
                        .with(
                                item -> {
                                    codec.writeResponse(s, jsonRpcRequest.getId(), item);
                                },
                                failure -> {
                                    codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod(), failure);
                                    this.subscriptions.remove(jsonRpcRequest.getId());
                                },
                                () -> this.subscriptions.remove(jsonRpcRequest.getId()));

                this.subscriptions.put(jsonRpcRequest.getId(), cancellable);
                codec.writeResponse(s, jsonRpcRequest.getId(), null);
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
                    LOG.errorf(e, "Unable to invoke method %s using JSON-RPC, request was: %s", jsonRpcRequest.getMethod(),
                            jsonRpcRequest);
                    codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod(), e);
                    return;
                }
                uni.subscribe()
                        .with(item -> {
                            codec.writeResponse(s, jsonRpcRequest.getId(), item);
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
                            codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod(), actualFailure);
                        });
            }
        } else {
            // Method not found
            codec.writeMethodNotFoundResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod());
        }
    }

    private Object[] getArgsAsObjects(Map<String, Class> params, JsonRPCRequest jsonRpcRequest) {
        List<Object> objects = new ArrayList<>();
        int cnt = 0;
        for (Map.Entry<String, Class> expectedParams : params.entrySet()) {
            String paramName = expectedParams.getKey();
            Class paramType = expectedParams.getValue();
            if (jsonRpcRequest.hasNamedParams()) {
                Object param = jsonRpcRequest.getNamedParam(paramName, paramType);
                objects.add(param);
            } else if (jsonRpcRequest.hasPositionedParams()) {
                Object param = jsonRpcRequest.getPositionedParam(++cnt, paramType);
                objects.add(param);
            }
        }
        return objects.toArray(Object[]::new);
    }
}
