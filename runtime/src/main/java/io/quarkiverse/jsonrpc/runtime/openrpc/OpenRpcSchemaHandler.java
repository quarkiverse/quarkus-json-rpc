package io.quarkiverse.jsonrpc.runtime.openrpc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class OpenRpcSchemaHandler implements Handler<RoutingContext> {
    private static final String ALLOWED_METHODS = "GET, OPTIONS";
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";

    private static final Logger LOGGER = Logger.getLogger(OpenRpcSchemaHandler.class);

    //private final SchemaPrinter schemaPrinter;

    public OpenRpcSchemaHandler() {
        //this.schemaPrinter = new SchemaPrinter();
    }

    @Override
    public void handle(RoutingContext event) {
        HttpServerRequest request = event.request();
        HttpServerResponse response = event.response();

        //TODO: Make this real
        String schemaErrorString = """
                {
                  "error": "Unable to generate OpenRPC schema"
                }
                """;

        Optional<String> schema = readSchemaFile();

        //OpenRpc openRpcSchema = CDI.current().select(OpenRpc.class).get();
        //String schemaString = schemaPrinter.print(openRpcSchema);

        if (request.method().equals(HttpMethod.OPTIONS)) {
            response.headers().set(HttpHeaders.ALLOW, ALLOWED_METHODS);
        } else if (request.method().equals(HttpMethod.GET)) {
            response.headers().set(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE);
            response.end(Buffer.buffer(schema.orElse(schemaErrorString)));
        } else {
            response.setStatusCode(405).end();
        }
    }

    Optional<String> readSchemaFile() {
        String schemaPath = ConfigProvider.getConfig()
                .getValue("quarkus.json-rpc.open-rpc.schema-path", String.class);
        try {
            Path path = Paths.get(System.getProperty("java.io.tmpdir"), schemaPath);
            String content = Files.readString(path);
            return content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (IOException e) {
            LOGGER.error("OpenRpcSchemaHandler.read() Unable to read file as " + schemaPath, e);
            return Optional.empty();
        }
    }
}
