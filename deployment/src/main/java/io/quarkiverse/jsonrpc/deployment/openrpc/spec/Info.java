package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

public record Info(
        String title,
        String description,
        String termsOfService,
        Contact contact,
        License license,
        String version) {
}
