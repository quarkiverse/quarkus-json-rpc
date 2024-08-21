package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

import java.util.Set;

// TODO: Extract OpenRPC spec to its own library
public record OpenRPC(
        OpenRPCSpecVersion openrpc,
        Info info,
        Set<Server> servers,
        Set<Method> methods
//components
//externalDocs
) {

}
