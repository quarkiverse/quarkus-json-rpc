package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

public record ContentDescriptor(
        String name,
        String summary,
        String description,
        boolean required,
        //TODO: Schema schema,
        boolean deprecated) {
}
