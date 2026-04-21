package io.quarkiverse.jsonrpc.runtime;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import io.quarkiverse.jsonrpc.runtime.model.ExecutionMode;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * Contains reflection info on the beans that needs to be called from the jsonrpc router
 */
public class ReflectionInfo {
    private final ExecutionMode executionMode;
    public Class bean;
    public Object instance;
    public Method method;
    public Map<String, Class> params;
    public java.lang.reflect.Type[] genericParameterTypes;

    public ReflectionInfo(Class bean, Object instance, Method method, Map<String, Class> params,
            ExecutionMode executionMode) {
        this.bean = bean;
        this.instance = instance;
        this.method = method;
        this.params = params;
        this.executionMode = executionMode;
        this.genericParameterTypes = resolveGenericParameterTypes();
    }

    /**
     * Resolve generic parameter types from the original bean class (not the CDI proxy),
     * because CDI proxies do not preserve generic type information.
     */
    private java.lang.reflect.Type[] resolveGenericParameterTypes() {
        try {
            Method beanMethod = bean.getMethod(method.getName(), method.getParameterTypes());
            return beanMethod.getGenericParameterTypes();
        } catch (NoSuchMethodException e) {
            return method.getGenericParameterTypes();
        }
    }

    public boolean isReturningMulti() {
        return Multi.class.isAssignableFrom(method.getReturnType());
    }

    public boolean isReturningUni() {
        return Uni.class.isAssignableFrom(method.getReturnType());
    }

    public boolean isReturningCompletionStage() {
        return CompletionStage.class.isAssignableFrom(method.getReturnType());
    }

    public boolean isReturningFlowPublisher() {
        return Flow.Publisher.class.isAssignableFrom(method.getReturnType());
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }
}
