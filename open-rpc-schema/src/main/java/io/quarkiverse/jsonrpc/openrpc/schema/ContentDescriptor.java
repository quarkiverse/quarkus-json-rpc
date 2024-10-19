package io.quarkiverse.jsonrpc.openrpc.schema;

public record ContentDescriptor(
        String name,
        String summary,
        String description,
        boolean required,
        //TODO: Schema schema,
        boolean deprecated) {
}
