package io.quarkiverse.jsonrpc.openrpc.annotations;

public @interface License {

    String name() default "";

    String url() default "";
}
