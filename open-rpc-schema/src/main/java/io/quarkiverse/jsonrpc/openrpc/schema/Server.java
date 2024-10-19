package io.quarkiverse.jsonrpc.openrpc.schema;

import java.util.Map;

public record Server(
        String name,
        String url,
        String summary,
        String description,
        Map<String, ServerVariable> variables) {
}
