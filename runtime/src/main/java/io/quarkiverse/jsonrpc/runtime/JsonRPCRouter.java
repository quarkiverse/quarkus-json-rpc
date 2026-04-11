package io.quarkiverse.jsonrpc.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCCodec;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCRequest;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
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

    private final JsonRPCCodec codec;

    private final Map<ServerWebSocket, Map<String, Cancellable>> socketSubscriptions = new ConcurrentHashMap<>();

    private final Map<ServerWebSocket, SecurityIdentity> socketIdentities = new ConcurrentHashMap<>();

    private volatile CurrentIdentityAssociation identityAssociation;
    private volatile boolean identityAssociationUnavailable;

    // Map json-rpc method to java in runtime classpath
    private final Map<String, ReflectionInfo> jsonRpcToJava = new HashMap<>();

    private final Map<String, String> orderedParameterKeyToNamedKey = new HashMap<>();

    private final JsonRPCSessions sessions;

    public JsonRPCRouter(JsonRPCCodec codec, JsonRPCSessions sessions,
            Map<JsonRPCMethodName, JsonRPCMethod> methodsMap) {
        this.codec = codec;
        this.sessions = sessions;
        populateJsonRPCMethods(methodsMap);
    }

    /**
     * This gets called on build to build into of the classes we are going to call in runtime
     *
     * @param methodsMap
     */
    private void populateJsonRPCMethods(Map<JsonRPCMethodName, JsonRPCMethod> methodsMap) {
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

    /**
     * Set the security identity captured during WebSocket upgrade into the current request context,
     * so CDI security interceptors ({@code @RolesAllowed}, {@code @Authenticated}, etc.) can authorize
     * method invocations.
     */
    private void setSecurityIdentity(ServerWebSocket socket) {
        SecurityIdentity identity = socketIdentities.get(socket);
        if (identity != null) {
            CurrentIdentityAssociation cia = getIdentityAssociation();
            if (cia != null) {
                cia.setIdentity(identity);
            }
        }
    }

    private CurrentIdentityAssociation getIdentityAssociation() {
        if (identityAssociationUnavailable) {
            return null;
        }
        CurrentIdentityAssociation result = identityAssociation;
        if (result == null) {
            try {
                result = Arc.container().select(CurrentIdentityAssociation.class).get();
                identityAssociation = result;
            } catch (jakarta.enterprise.inject.UnsatisfiedResolutionException e) {
                LOG.debugf("CurrentIdentityAssociation not available — no security extension present");
                identityAssociationUnavailable = true;
                return null;
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Uni<?> invoke(ReflectionInfo info, Object target, Object[] args, ServerWebSocket socket) {
        Context vc = Vertx.currentContext();

        ManagedContext currentManagedContext = Arc.container().requestContext();
        boolean activated = false;
        try {
            if (!currentManagedContext.isActive()) {
                currentManagedContext.activate();
                activated = true;
            }
            setSecurityIdentity(socket);
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
            } else if (info.isReturningCompletionStage()) {
                if (info.isExplicitlyBlocking()) {
                    SmallRyeThreadContext threadContext = Arc.container().select(SmallRyeThreadContext.class).get();
                    final Promise<?> result = Promise.promise();
                    Callable<Object> contextualCallable = threadContext.contextualCallable(() -> {
                        CompletionStage<?> cs = (CompletionStage<?>) info.method.invoke(target, args);
                        return cs.toCompletableFuture().get();
                    });
                    vc.executeBlocking(contextualCallable).onComplete((Handler<AsyncResult<Object>>) result);
                    return Uni.createFrom().completionStage(result.future().toCompletionStage());
                } else {
                    CompletionStage<?> cs = (CompletionStage<?>) info.method.invoke(target, args);
                    return Uni.createFrom().completionStage(cs);
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
            if (activated) {
                currentManagedContext.deactivate();
            }
        }
    }

    public Map<String, ReflectionInfo> getRegisteredMethods() {
        return Collections.unmodifiableMap(jsonRpcToJava);
    }

    public Map<ServerWebSocket, Map<String, Cancellable>> getSocketSubscriptions() {
        return Collections.unmodifiableMap(socketSubscriptions);
    }

    public void addSocket(ServerWebSocket socket) {
        addSocket(socket, null);
    }

    public void addSocket(ServerWebSocket socket, SecurityIdentity identity) {
        sessions.addSession(socket);
        if (identity != null && !identity.isAnonymous()) {
            socketIdentities.put(socket, identity);
        }
        socket.textMessageHandler((e) -> {
            JsonRPCRequest jsonRpcRequest = codec.readRequest(e);
            route(jsonRpcRequest, socket);
        });
        socket.closeHandler((e) -> {
            sessions.removeSession(socket);
            socketIdentities.remove(socket);
            Map<String, Cancellable> subs = socketSubscriptions.remove(socket);
            if (subs != null) {
                for (Map.Entry<String, Cancellable> entry : subs.entrySet()) {
                    try {
                        entry.getValue().cancel();
                    } catch (Exception ex) {
                        LOG.warnf(ex, "Failed to cancel subscription %s on WebSocket close", entry.getKey());
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void route(JsonRPCRequest jsonRpcRequest, ServerWebSocket s) {
        // Handle unsubscribe requests before normal method lookup
        if (JsonRPCKeys.UNSUBSCRIBE.equals(jsonRpcRequest.getMethod())) {
            handleUnsubscribe(jsonRpcRequest, s);
            return;
        }

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

            if (reflectionInfo.isReturningMulti() || reflectionInfo.isReturningFlowPublisher()) {
                Multi<?> multi;
                ManagedContext requestContext = Arc.container().requestContext();
                boolean activated = false;
                try {
                    if (!requestContext.isActive()) {
                        requestContext.activate();
                        activated = true;
                    }
                    setSecurityIdentity(s);

                    Object result;
                    if (jsonRpcRequest.hasParams()) {
                        Object[] args = getArgsAsObjects(reflectionInfo, jsonRpcRequest);
                        result = reflectionInfo.method.invoke(target, args);
                    } else {
                        result = reflectionInfo.method.invoke(target);
                    }
                    if (result instanceof Multi) {
                        multi = (Multi<?>) result;
                    } else {
                        multi = Multi.createFrom().publisher((Flow.Publisher<?>) result);
                    }
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to invoke method %s using JSON-RPC, request was: %s", jsonRpcRequest.getMethod(),
                            jsonRpcRequest);
                    codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod(), unwrap(e));
                    return;
                } finally {
                    if (activated) {
                        requestContext.deactivate();
                    }
                }

                String subscriptionId = UUID.randomUUID().toString();
                String methodName = jsonRpcRequest.getMethod();
                Map<String, Cancellable> subs = this.socketSubscriptions.computeIfAbsent(s,
                        k -> new ConcurrentHashMap<>());

                // Use AtomicReference so cancel always targets the real cancellable,
                // even if unsubscribe arrives before subscribe() returns
                AtomicReference<Cancellable> ref = new AtomicReference<>(() -> {
                });
                subs.put(subscriptionId, () -> ref.get().cancel());

                // Send ack before subscribing so client has subscription ID before items arrive
                codec.writeResponse(s, jsonRpcRequest.getId(), subscriptionId);

                Cancellable cancellable = multi.subscribe()
                        .with(
                                item -> codec.writeSubscriptionItem(s, subscriptionId, item),
                                failure -> {
                                    codec.writeSubscriptionError(s, subscriptionId, methodName, unwrap(failure));
                                    subs.remove(subscriptionId);
                                },
                                () -> {
                                    codec.writeSubscriptionComplete(s, subscriptionId);
                                    subs.remove(subscriptionId);
                                });

                ref.set(cancellable);
            } else {
                // The invocation will return a Uni<JsonObject>
                Uni<?> uni;
                try {
                    if (jsonRpcRequest.hasParams()) {
                        Object[] args = getArgsAsObjects(reflectionInfo, jsonRpcRequest);
                        uni = invoke(reflectionInfo, target, args, s);
                    } else {
                        uni = invoke(reflectionInfo, target, new Object[0], s);
                    }
                } catch (Exception e) {
                    LOG.errorf(e, "Unable to invoke method %s using JSON-RPC, request was: %s", jsonRpcRequest.getMethod(),
                            jsonRpcRequest);
                    codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod(), unwrap(e));
                    return;
                }
                uni.subscribe()
                        .with(item -> {
                            codec.writeResponse(s, jsonRpcRequest.getId(), item);
                        }, failure -> {
                            codec.writeErrorResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod(), unwrap(failure));
                        });
            }
        } else {
            // Method not found
            codec.writeMethodNotFoundResponse(s, jsonRpcRequest.getId(), jsonRpcRequest.getMethod());
        }
    }

    private void handleUnsubscribe(JsonRPCRequest jsonRpcRequest, ServerWebSocket s) {
        String subscriptionId = null;
        if (jsonRpcRequest.hasPositionedParams()) {
            Object[] params = jsonRpcRequest.getPositionedParams();
            if (params.length > 0) {
                subscriptionId = String.valueOf(params[0]);
            }
        } else if (jsonRpcRequest.hasNamedParams()) {
            subscriptionId = jsonRpcRequest.getNamedParam(JsonRPCKeys.SUBSCRIPTION, String.class);
        }

        if (subscriptionId == null) {
            codec.writeErrorResponse(s, jsonRpcRequest.getId(), JsonRPCKeys.INVALID_PARAMS, JsonRPCKeys.UNSUBSCRIBE,
                    new IllegalArgumentException("Missing required parameter: subscription ID"));
            return;
        }

        Map<String, Cancellable> subs = socketSubscriptions.get(s);
        if (subs != null) {
            Cancellable cancellable = subs.remove(subscriptionId);
            if (cancellable != null) {
                cancellable.cancel();
                codec.writeResponse(s, jsonRpcRequest.getId(), true);
                return;
            }
        }
        codec.writeResponse(s, jsonRpcRequest.getId(), false);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        if (current instanceof InvocationTargetException f && f.getTargetException() != null) {
            current = f.getTargetException();
        } else if (current.getCause() instanceof InvocationTargetException f && f.getTargetException() != null) {
            current = f.getTargetException();
        }
        if (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private Object[] getArgsAsObjects(ReflectionInfo reflectionInfo, JsonRPCRequest jsonRpcRequest) {
        Map<String, Class> params = reflectionInfo.params;
        java.lang.reflect.Type[] genericTypes = reflectionInfo.genericParameterTypes;

        List<Object> objects = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<String, Class> expectedParams : params.entrySet()) {
            String paramName = expectedParams.getKey();
            java.lang.reflect.Type genericType = genericTypes[idx];
            if (jsonRpcRequest.hasNamedParams()) {
                Object param = jsonRpcRequest.getNamedParam(paramName, genericType);
                objects.add(param);
            } else if (jsonRpcRequest.hasPositionedParams()) {
                Object param = jsonRpcRequest.getPositionedParam(idx + 1, genericType);
                objects.add(param);
            }
            idx++;
        }
        return objects.toArray(Object[]::new);
    }
}
