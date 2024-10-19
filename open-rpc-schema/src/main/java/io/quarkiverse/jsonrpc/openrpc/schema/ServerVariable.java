package io.quarkiverse.jsonrpc.openrpc.schema;

import java.util.Set;

// TODO: Fix Server Variable names
public record ServerVariable(
        Set<String> lenum,
        String ldefault,
        String description) {
}
