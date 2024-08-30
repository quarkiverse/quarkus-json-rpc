package io.quarkiverse.jsonrpc.openrpc.annotations;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation provides metadata about the API, and maps to the Info object in OpenRPC Specification 2.
 *
 * @see <a href="https://spec.open-rpc.org/#info-object">OpenRPC Specification Info Object</a>
 **/
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Info {

    /**
     * The title of the application.
     *
     * @return the application's title
     **/
    String title();

    String description() default "";

    String termsOfService() default "";

    Contact contact() default @Contact();

    License license() default @License();

    /**
     * The version of the API definition.
     *
     * @return the application's version
     **/
    String version();

}
