package io.quarkiverse.jsonrpc.openrpc.schema;

public record Info(
        String title,
        String description,
        String termsOfService,
        Contact contact,
        License license,
        String version) {
}
