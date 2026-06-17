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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import io.quarkiverse.jsonrpc.api.JsonRPCError;
import io.quarkiverse.jsonrpc.api.JsonRPCExceptionMapper;
import io.quarkiverse.jsonrpc.runtime.model.ExecutionMode;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCCodec;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCKeys;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCRequest;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCResponse;
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

    private volatile List<JsonRPCExceptionMapper> exceptionMappers;

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

    private static JsonRPCMetricsHandler metrics() {
        return JsonRPCRecorder.metrics;
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
                        params, jsonRpcMethod.getExecutionMode());
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
            ExecutionMode mode = info.getExecutionMode();
            if (info.isReturningUni()) {
                if (mode == ExecutionMode.BLOCKING || mode == ExecutionMode.VIRTUAL_THREAD) {
                    SmallRyeThreadContext threadContext = Arc.container().select(SmallRyeThreadContext.class).get();
                    final Promise<?> result = Promise.promise();
                    Callable<Object> contextualCallable = threadContext.contextualCallable(() -> {
                        Object resultFromMethodCall = info.method.invoke(target, args);
                        Uni<?> uniFromMethodCall = (Uni<?>) resultFromMethodCall;
                        return uniFromMethodCall.subscribeAsCompletionStage().get();
                    });
                    dispatchBlocking(vc, contextualCallable, result, mode);
                    return Uni.createFrom().completionStage(result.future().toCompletionStage());
                } else {
                    return (Uni<?>) info.method.invoke(target, args);
                }
            } else if (info.isReturningCompletionStage()) {
                if (mode == ExecutionMode.BLOCKING || mode == ExecutionMode.VIRTUAL_THREAD) {
                    SmallRyeThreadContext threadContext = Arc.container().select(SmallRyeThreadContext.class).get();
                    final Promise<?> result = Promise.promise();
                    Callable<Object> contextualCallable = threadContext.contextualCallable(() -> {
                        CompletionStage<?> cs = (CompletionStage<?>) info.method.invoke(target, args);
                        return cs.toCompletableFuture().get();
                    });
                    dispatchBlocking(vc, contextualCallable, result, mode);
                    return Uni.createFrom().completionStage(result.future().toCompletionStage());
                } else {
                    CompletionStage<?> cs = (CompletionStage<?>) info.method.invoke(target, args);
                    return Uni.createFrom().completionStage(cs);
                }
            } else {
                if (mode == ExecutionMode.NON_BLOCKING) {
                    return Uni.createFrom().item(Unchecked.supplier(() -> info.method.invoke(target, args)));
                } else {
                    SmallRyeThreadContext threadContext = Arc.container().select(SmallRyeThreadContext.class).get();
                    final Promise<?> result = Promise.promise();
                    Callable<Object> contextualCallable = threadContext.contextualCallable(() -> {
                        return info.method.invoke(target, args);
                    });
                    dispatchBlocking(vc, contextualCallable, result, mode);
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

    @SuppressWarnings("unchecked")
    private static void dispatchBlocking(Context vc, Callable<Object> callable, Promise<?> result,
            ExecutionMode mode) {
        if (mode == ExecutionMode.VIRTUAL_THREAD) {
            VirtualThreadSupport.executeBlocking(callable, result);
        } else {
            vc.executeBlocking(callable).onComplete((Handler<AsyncResult<Object>>) result);
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
        JsonRPCMetricsHandler m = metrics();
        if (m != null) {
            m.connectionOpened();
        }
        if (identity != null && !identity.isAnonymous()) {
            socketIdentities.put(socket, identity);
        }
        socket.textMessageHandler((e) -> {
            try {
                JsonNode jsonNode;
                try {
                    jsonNode = codec.parseJson(e);
                } catch (JsonProcessingException ex) {
                    codec.writeResponse(socket,
                            new JsonRPCResponse<>(NullNode.instance,
                                    new JsonRPCResponse.Error(JsonRPCKeys.PARSE_ERROR, "Parse error")));
                    return;
                }

                if (jsonNode.isArray()) {
                    if (jsonNode.isEmpty()) {
                        codec.writeResponse(socket, new JsonRPCResponse<>(NullNode.instance,
                                new JsonRPCResponse.Error(JsonRPCKeys.INVALID_REQUEST,
                                        "Invalid request: empty batch")));
                        return;
                    }
                    List<JsonNode> elements = new ArrayList<>();
                    for (JsonNode element : jsonNode) {
                        elements.add(element);
                    }
                    routeBatch(elements, socket);
                } else {
                    if (!jsonNode.isObject() || !jsonNode.has(JsonRPCKeys.METHOD)) {
                        JsonNode id = jsonNode.isObject() && jsonNode.has(JsonRPCKeys.ID)
                                ? jsonNode.get(JsonRPCKeys.ID)
                                : NullNode.instance;
                        codec.writeResponse(socket, new JsonRPCResponse<>(id,
                                new JsonRPCResponse.Error(JsonRPCKeys.INVALID_REQUEST, "Invalid request")));
                        return;
                    }
                    JsonRPCRequest jsonRpcRequest = codec.readRequest(jsonNode);
                    route(jsonRpcRequest, socket);
                }
            } catch (Exception ex) {
                LOG.errorf(ex, "Unexpected error processing JSON-RPC message");
                codec.writeResponse(socket, new JsonRPCResponse<>(NullNode.instance,
                        new JsonRPCResponse.Error(JsonRPCKeys.INTERNAL_ERROR, "Internal error")));
            }
        });
        socket.closeHandler((e) -> {
            sessions.removeSession(socket);
            socketIdentities.remove(socket);
            JsonRPCMetricsHandler mc = metrics();
            if (mc != null) {
                mc.connectionClosed();
            }
            Map<String, Cancellable> subs = socketSubscriptions.remove(socket);
            if (subs != null) {
                for (Map.Entry<String, Cancellable> entry : subs.entrySet()) {
                    try {
                        entry.getValue().cancel();
                    } catch (Exception ex) {
                        LOG.warnf(ex, "Failed to cancel subscription %s on WebSocket close", entry.getKey());
                    }
                    if (mc != null) {
                        mc.subscriptionEnded();
                    }
                }
            }
        });
    }

    private void route(JsonRPCRequest jsonRpcRequest, ServerWebSocket s) {
        boolean notification = jsonRpcRequest.isNotification();
        if (JsonRPCHotReplacementSetup.isEnabled()) {
            Vertx.currentContext().<Void> executeBlocking(() -> {
                JsonRPCHotReplacementSetup.scan();
                return null;
            }).onComplete(ar -> {
                if (ar.failed()) {
                    LOG.warnf(ar.cause(), "JSON-RPC hot reload scan failed");
                }
                dispatchRoute(jsonRpcRequest, s)
                        .subscribe().with(result -> {
                            if (!notification) {
                                codec.writeResponse(s, result.response);
                                result.runPostWrite();
                            }
                        });
            });
        } else {
            dispatchRoute(jsonRpcRequest, s)
                    .subscribe().with(result -> {
                        if (!notification) {
                            codec.writeResponse(s, result.response);
                            result.runPostWrite();
                        }
                    });
        }
    }

    private void routeBatch(List<JsonNode> elements, ServerWebSocket s) {
        Runnable dispatch = () -> {
            List<Uni<DispatchResult>> unis = new ArrayList<>();
            for (JsonNode element : elements) {
                if (!element.isObject() || !element.has(JsonRPCKeys.METHOD)) {
                    JsonNode id = element.isObject() && element.has(JsonRPCKeys.ID) ? element.get(JsonRPCKeys.ID)
                            : NullNode.instance;
                    unis.add(Uni.createFrom().item(new DispatchResult(new JsonRPCResponse<>(id,
                            new JsonRPCResponse.Error(JsonRPCKeys.INVALID_REQUEST, "Invalid request")))));
                } else {
                    JsonRPCRequest request = codec.readRequest(element);
                    boolean notification = request.isNotification();
                    Uni<DispatchResult> uni = dispatchRoute(request, s);
                    if (notification) {
                        uni = uni.map(r -> new DispatchResult(r.response, r.postWrite, true));
                    }
                    unis.add(uni);
                }
            }
            Uni.join().all(unis).andCollectFailures()
                    .subscribe().with(
                            results -> {
                                List<JsonRPCResponse<?>> responses = new ArrayList<>();
                                List<Runnable> postWrites = new ArrayList<>();
                                for (DispatchResult result : results) {
                                    if (!result.notification) {
                                        responses.add(result.response);
                                        if (result.postWrite != null) {
                                            postWrites.add(result.postWrite);
                                        }
                                    }
                                }
                                if (!responses.isEmpty()) {
                                    codec.writeBatchResponse(s, responses);
                                }
                                postWrites.forEach(Runnable::run);
                            },
                            failure -> {
                                LOG.errorf(failure, "Unexpected error processing batch request");
                                codec.writeResponse(s, new JsonRPCResponse<>(NullNode.instance,
                                        new JsonRPCResponse.Error(JsonRPCKeys.INTERNAL_ERROR,
                                                "Internal error processing batch")));
                            });
        };

        if (JsonRPCHotReplacementSetup.isEnabled()) {
            Vertx.currentContext().<Void> executeBlocking(() -> {
                JsonRPCHotReplacementSetup.scan();
                return null;
            }).onComplete(ar -> {
                if (ar.failed()) {
                    LOG.warnf(ar.cause(), "JSON-RPC hot reload scan failed");
                }
                dispatch.run();
            });
        } else {
            dispatch.run();
        }
    }

    private record DispatchResult(JsonRPCResponse<?> response, Runnable postWrite, boolean notification) {
        DispatchResult(JsonRPCResponse<?> response) {
            this(response, null, false);
        }

        void runPostWrite() {
            if (postWrite != null) {
                postWrite.run();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Uni<DispatchResult> dispatchRoute(JsonRPCRequest jsonRpcRequest, ServerWebSocket s) {
        if (JsonRPCKeys.UNSUBSCRIBE.equals(jsonRpcRequest.getMethod())) {
            return handleUnsubscribe(jsonRpcRequest, s)
                    .map(DispatchResult::new);
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
            String methodName = jsonRpcRequest.getMethod();
            JsonRPCMetricsHandler m = metrics();
            long startNanos = m != null ? System.nanoTime() : 0;

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
                    if (m != null) {
                        m.recordError(methodName, System.nanoTime() - startNanos);
                    }
                    LOG.errorf(e, "Unable to invoke method %s using JSON-RPC, request was: %s", methodName,
                            jsonRpcRequest);
                    return Uni.createFrom()
                            .item(new DispatchResult(errorResponse(jsonRpcRequest.getId(), methodName, unwrap(e))));
                } finally {
                    if (activated) {
                        requestContext.deactivate();
                    }
                }

                if (m != null) {
                    m.recordSuccess(methodName, System.nanoTime() - startNanos);
                }

                String subscriptionId = UUID.randomUUID().toString();
                Map<String, Cancellable> subs = this.socketSubscriptions.computeIfAbsent(s,
                        k -> new ConcurrentHashMap<>());

                AtomicReference<Cancellable> ref = new AtomicReference<>(() -> {
                });
                subs.put(subscriptionId, () -> ref.get().cancel());

                final Multi<?> streamSource = multi;
                if (m != null) {
                    m.recordSuccess(methodName, System.nanoTime() - startNanos);
                }

                // Defer Multi subscription until after the ack response is written,
                // so synchronous Multi items don't arrive before the subscription ID
                Runnable startSubscription = () -> {
                    if (m != null) {
                        m.subscriptionStarted();
                    }
                    Cancellable cancellable = streamSource.subscribe()
                            .with(
                                    item -> codec.writeSubscriptionItem(s, subscriptionId, item),
                                    failure -> {
                                        Throwable cause = unwrap(failure);
                                        if (m != null) {
                                            m.recordSubscriptionError(methodName);
                                            m.subscriptionEnded();
                                        }
                                        LOG.error("Error in JsonRPC subscription", cause);
                                        codec.writeSubscriptionError(s, subscriptionId,
                                                resolveError(methodName, cause));
                                        subs.remove(subscriptionId);
                                    },
                                    () -> {
                                        if (m != null) {
                                            m.subscriptionEnded();
                                        }
                                        codec.writeSubscriptionComplete(s, subscriptionId);
                                        subs.remove(subscriptionId);
                                    });
                    ref.set(cancellable);
                };

                JsonRPCResponse<?> ack = new JsonRPCResponse<>(jsonRpcRequest.getId(), (Object) subscriptionId);
                return Uni.createFrom().item(new DispatchResult(ack, startSubscription, false));
            } else {
                Uni<?> uni;
                try {
                    if (jsonRpcRequest.hasParams()) {
                        Object[] args = getArgsAsObjects(reflectionInfo, jsonRpcRequest);
                        uni = invoke(reflectionInfo, target, args, s);
                    } else {
                        uni = invoke(reflectionInfo, target, new Object[0], s);
                    }
                } catch (Exception e) {
                    if (m != null) {
                        m.recordError(methodName, System.nanoTime() - startNanos);
                    }
                    LOG.errorf(e, "Unable to invoke method %s using JSON-RPC, request was: %s", methodName,
                            jsonRpcRequest);
                    return Uni.createFrom()
                            .item(new DispatchResult(errorResponse(jsonRpcRequest.getId(), methodName, unwrap(e))));
                }
                return uni
                        .<DispatchResult> map(item -> {
                            if (m != null) {
                                m.recordSuccess(methodName, System.nanoTime() - startNanos);
                            }
                            return new DispatchResult(new JsonRPCResponse<>(jsonRpcRequest.getId(), item));
                        })
                        .onFailure().recoverWithItem(failure -> {
                            if (m != null) {
                                m.recordError(methodName, System.nanoTime() - startNanos);
                            }
                            return new DispatchResult(
                                    errorResponse(jsonRpcRequest.getId(), methodName, unwrap(failure)));
                        });
            }
        } else {
            return Uni.createFrom().item(new DispatchResult(new JsonRPCResponse<>(jsonRpcRequest.getId(),
                    new JsonRPCResponse.Error(JsonRPCKeys.METHOD_NOT_FOUND,
                            "Method [" + jsonRpcRequest.getMethod() + "] not found"))));
        }
    }

    private JsonRPCResponse<?> errorResponse(JsonNode id, String methodName, Throwable exception) {
        JsonRPCResponse.Error error = resolveError(methodName, exception);
        LOG.error("Error in JsonRPC Call", exception);
        return new JsonRPCResponse<>(id, error);
    }

    private JsonRPCResponse.Error resolveError(String methodName, Throwable exception) {
        for (JsonRPCExceptionMapper mapper : getExceptionMappers()) {
            try {
                JsonRPCError mapped = mapper.mapException(exception);
                if (mapped != null) {
                    return new JsonRPCResponse.Error(mapped.code(), mapped.message(), mapped.data());
                }
            } catch (Exception e) {
                LOG.warnf(e, "Exception mapper %s threw while handling %s",
                        mapper.getClass().getName(), exception.getClass().getName());
            }
        }
        if (exception instanceof io.quarkus.security.UnauthorizedException) {
            return new JsonRPCResponse.Error(JsonRPCKeys.UNAUTHORIZED,
                    "Method [" + methodName + "] failed: " + exception.getMessage());
        }
        if (exception instanceof io.quarkus.security.ForbiddenException) {
            return new JsonRPCResponse.Error(JsonRPCKeys.FORBIDDEN,
                    "Method [" + methodName + "] failed: " + exception.getMessage());
        }
        return new JsonRPCResponse.Error(JsonRPCKeys.INTERNAL_ERROR,
                "Method [" + methodName + "] failed: " + exception.getMessage());
    }

    private List<JsonRPCExceptionMapper> getExceptionMappers() {
        List<JsonRPCExceptionMapper> local = exceptionMappers;
        if (local == null) {
            local = Arc.container().select(JsonRPCExceptionMapper.class)
                    .stream().toList();
            exceptionMappers = local;
        }
        return local;
    }

    private Uni<JsonRPCResponse<?>> handleUnsubscribe(JsonRPCRequest jsonRpcRequest, ServerWebSocket s) {
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
            return Uni.createFrom().item(new JsonRPCResponse<>(jsonRpcRequest.getId(),
                    new JsonRPCResponse.Error(JsonRPCKeys.INVALID_PARAMS,
                            "Method [" + JsonRPCKeys.UNSUBSCRIBE
                                    + "] failed: Missing required parameter: subscription ID")));
        }

        Map<String, Cancellable> subs = socketSubscriptions.get(s);
        if (subs != null) {
            Cancellable cancellable = subs.remove(subscriptionId);
            if (cancellable != null) {
                cancellable.cancel();
                JsonRPCMetricsHandler m = metrics();
                if (m != null) {
                    m.subscriptionEnded();
                }
                return Uni.createFrom().item(new JsonRPCResponse<>(jsonRpcRequest.getId(), (Object) true));
            }
        }
        return Uni.createFrom().item(new JsonRPCResponse<>(jsonRpcRequest.getId(), (Object) false));
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
