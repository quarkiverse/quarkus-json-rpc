package io.quarkiverse.jsonrpc.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.openrpc.schema.OpenRpc;
import io.quarkiverse.jsonrpc.runtime.config.JsonRpcConfig;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkiverse.jsonrpc.runtime.openrpc.OpenRpcSchemaHandler;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class JsonRPCRecorder {

    private static final Logger LOGGER = Logger.getLogger(JsonRPCRecorder.class);

    public void createJsonRpcRouter(BeanContainer beanContainer,
            Map<JsonRPCMethodName, JsonRPCMethod> methodsMap) {
        JsonRPCRouter jsonRpcRouter = beanContainer.beanInstance(JsonRPCRouter.class);
        jsonRpcRouter.populateJsonRPCMethods(methodsMap);
    }

    public Handler<RoutingContext> webSocketHandler() {
        return new JsonRPCWebSocket();
    }

    public Handler<RoutingContext> schemaHandler() {
        return new OpenRpcSchemaHandler();
    }

    public void storeOpenRpcSchema(OpenRpc model, JsonRpcConfig jsonRpcConfig) {
        final var schemaConfig = jsonRpcConfig.openRpc;
        //        String filePath = schemaConfig.storeSchemaDirectory + "/" + schemaConfig.schemaPath;
        try {
            Path path = Paths.get(System.getProperty("java.io.tmpdir"), schemaConfig.schemaPath);
            String content = ObjectMapperFactory.json().writeValueAsString(model);
            LOGGER.info("JsonRPCRecorder.store to " + path);
            Files.writeString(path, content);
        } catch (IOException e) {
            LOGGER.error("io.quarkiverse.asyncapi.annotation.scanner.AsyncApiRecorder", "store", e);
        }
    }
}
