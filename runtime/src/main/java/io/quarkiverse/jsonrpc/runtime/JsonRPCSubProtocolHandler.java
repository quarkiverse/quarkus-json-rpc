package io.quarkiverse.jsonrpc.runtime;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jboss.logging.Logger;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Extracts HTTP headers encoded in the {@code Sec-WebSocket-Protocol} header
 * using the Quarkus {@code quarkus-http-upgrade} convention.
 * <p>
 * JavaScript's {@code WebSocket} API does not support custom headers, so bearer
 * tokens are encoded into the sub-protocol header during the upgrade handshake:
 *
 * <pre>
 * Sec-WebSocket-Protocol: bearer-token-carrier, quarkus-http-upgrade%23Authorization%23Bearer%20token
 * </pre>
 * <p>
 * This handler decodes those entries and sets them as real HTTP headers on the
 * request so that Quarkus authentication mechanisms can process them normally.
 * The encoded entries are removed from {@code Sec-WebSocket-Protocol}, leaving
 * only real sub-protocol names.
 * <p>
 * Only headers in the {@link #ALLOWED_HEADERS} allowlist are accepted to prevent
 * arbitrary header injection. The filter is scoped to the configured JSON-RPC
 * WebSocket path.
 */
public class JsonRPCSubProtocolHandler implements Handler<RoutingContext> {
    private static final Logger LOG = Logger.getLogger(JsonRPCSubProtocolHandler.class.getName());

    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String UPGRADE = "Upgrade";
    private static final String WEBSOCKET = "websocket";
    private static final String QUARKUS_HTTP_UPGRADE_PREFIX = "quarkus-http-upgrade#";

    private static final Set<String> ALLOWED_HEADERS = Set.of("authorization");

    private final String wsPath;

    public JsonRPCSubProtocolHandler(String wsPath) {
        this.wsPath = wsPath;
    }

    @Override
    public void handle(RoutingContext event) {
        if (!event.request().path().equals(wsPath)) {
            event.next();
            return;
        }

        if (!WEBSOCKET.equalsIgnoreCase(event.request().getHeader(UPGRADE))) {
            event.next();
            return;
        }

        String header = event.request().getHeader(SEC_WEBSOCKET_PROTOCOL);
        if (header == null || header.isEmpty()) {
            event.next();
            return;
        }

        List<String> realProtocols = new ArrayList<>();
        for (String raw : header.split(",")) {
            String trimmed = raw.trim();
            String decoded = URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
            if (decoded.startsWith(QUARKUS_HTTP_UPGRADE_PREFIX)) {
                // Format: quarkus-http-upgrade#HeaderName#HeaderValue
                String rest = decoded.substring(QUARKUS_HTTP_UPGRADE_PREFIX.length());
                int hashIdx = rest.indexOf('#');
                if (hashIdx > 0) {
                    String headerName = rest.substring(0, hashIdx);
                    if (ALLOWED_HEADERS.contains(headerName.toLowerCase(Locale.ROOT))) {
                        String headerValue = rest.substring(hashIdx + 1);
                        event.request().headers().set(headerName, headerValue);
                        LOG.debugf("Extracted header from Sec-WebSocket-Protocol: %s", headerName);
                    } else {
                        LOG.warnf("Rejected disallowed header from Sec-WebSocket-Protocol: %s", headerName);
                    }
                }
            } else {
                realProtocols.add(trimmed);
            }
        }

        if (realProtocols.isEmpty()) {
            event.request().headers().remove(SEC_WEBSOCKET_PROTOCOL);
        } else {
            event.request().headers().set(SEC_WEBSOCKET_PROTOCOL, String.join(", ", realProtocols));
        }

        event.next();
    }
}
