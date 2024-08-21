package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

import java.util.Map;

public record Server(
        String name,
        String url,
        String summary,
        String description,
        Map<String, ServerVariable> variables) {
}
