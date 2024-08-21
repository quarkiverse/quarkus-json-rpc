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
//TODO: Set<OpenRPCError> errors,
//TODO: Set<OpenRPCLink> links,
//TODO: paramStructure
//TODO: examples
) {
}
