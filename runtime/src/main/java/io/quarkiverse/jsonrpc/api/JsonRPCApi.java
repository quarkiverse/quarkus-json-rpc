package io.quarkiverse.jsonrpc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Json-RPC Endpoint.
 * TODO: Allow adding path (from ws)?
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JsonRPCApi {
    /**
     * @return a identifier/scope
     */
    String value() default "_DEFAULT_SCOPE_";
}
