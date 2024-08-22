package io.quarkiverse.jsonrpc.runtime;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class OpenRPCSchemaHandler implements Handler<RoutingContext> {
    private static final String ALLOWED_METHODS = "GET, OPTIONS";
    private static final String CONTENT_TYPE = "text/plain; charset=UTF-8";

    //private final SchemaPrinter schemaPrinter;

    public OpenRPCSchemaHandler() {
        //this.schemaPrinter = new SchemaPrinter();
    }

    @Override
    public void handle(RoutingContext event) {
        HttpServerRequest request = event.request();
        HttpServerResponse response = event.response();

        //TODO: Make this real
        String schemaString = """
                {
                  "openrpc" : "1.2.6",
                  "info" : {
                    "title" : "example",
                    "description" : "example",
                    "termsOfService" : "https://apache.com",
                    "contact" : {
                      "name" : "Alexander Haslam",
                      "url" : "https://indiealex.com",
                      "email" : "alex@indiealexh.com"
                    },
                    "license" : {
                      "name" : "Apache 2.0",
                      "url" : "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    },
                    "version" : "1.0.0"
                  },
                  "servers" : [ ],
                  "methods" : [ ]
                }
                """;

        //OpenRPC graphQLSchema = CDI.current().select(OpenRPC.class).get();

        //String schemaString = schemaPrinter.print(graphQLSchema);

        if (request.method().equals(HttpMethod.OPTIONS)) {
            response.headers().set(HttpHeaders.ALLOW, ALLOWED_METHODS);
        } else if (request.method().equals(HttpMethod.GET)) {
            response.headers().set(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE);
            response.end(Buffer.buffer(schemaString));
        } else {
            response.setStatusCode(405).end();
        }
    }
}
