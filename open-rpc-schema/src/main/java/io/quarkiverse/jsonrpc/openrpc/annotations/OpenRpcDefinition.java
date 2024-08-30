package io.quarkiverse.jsonrpc.openrpc.annotations;

import java.lang.annotation.*;

@Target({ ElementType.TYPE, ElementType.PACKAGE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface OpenRpcDefinition {
    /**
     * Required: Provides metadata about the API. The metadata MAY be used by tooling as required.
     *
     * @return the metadata about this API
     * @see <a href="https://spec.open-rpc.org/#info-object">OpenRPC Spec Info Object</a>
     */
    Info info();
}
