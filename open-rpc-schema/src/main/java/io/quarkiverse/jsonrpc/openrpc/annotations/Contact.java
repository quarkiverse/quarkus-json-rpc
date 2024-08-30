package io.quarkiverse.jsonrpc.openrpc.annotations;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Contact {

    String name() default "";

    String url() default "";

    String email() default "";
}
