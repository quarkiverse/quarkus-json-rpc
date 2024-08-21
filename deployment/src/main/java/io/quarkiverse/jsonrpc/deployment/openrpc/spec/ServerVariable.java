package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

import java.util.Set;

// TODO: Fix Server Variable names
public record ServerVariable(
        Set<String> lenum,
        String ldefault,
        String description) {
}
