package io.quarkiverse.jsonrpc.runtime;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * Contains reflection info on the beans that needs to be called from the jsonrpc router
 */
public class ReflectionInfo {
    private final boolean blocking;
    private final boolean nonBlocking;
    public Class bean;
    public Object instance;
    public Method method;
    public Map<String, Class> params;

    public ReflectionInfo(Class bean, Object instance, Method method, Map<String, Class> params, boolean explicitlyBlocking,
            boolean explicitlyNonBlocking) {
        this.bean = bean;
        this.instance = instance;
        this.method = method;
        this.params = params;
        this.blocking = explicitlyBlocking;
        this.nonBlocking = explicitlyNonBlocking;
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

    public boolean isExplicitlyBlocking() {
        return blocking;
    }

    public boolean isExplicitlyNonBlocking() {
        return nonBlocking;
    }
}
