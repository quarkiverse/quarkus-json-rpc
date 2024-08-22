package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

import java.util.Set;

public record Method(
        String name,
        //TODO: Set<Tag> tags,
        String summary,
        String description,
        //TODO: ExternalDocumentation externalDocs
        ContentDescriptor params,
        //TODO: ContentDescriptor result
        boolean deprecated,
        Set<Server> servers
//TODO: Set<OpenRpcError> errors,
//TODO: Set<OpenRpcLink> links,
//TODO: paramStructure
//TODO: examples
) {
}
