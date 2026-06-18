package io.quarkiverse.jsonrpc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Json-RPC Endpoint.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JsonRPCApi {
    /**
     * @return a identifier/scope
     */
    String value() default "_DEFAULT_SCOPE_";

    /**
     * Optional WebSocket path for this API group.
     * When set, a separate WebSocket route is registered at this path
     * in addition to the default global path.
     * When left empty (default), the class's methods are served on the
     * global path configured via {@code quarkus.json-rpc.web-socket.path}.
     */
    String path() default "";
}
