package io.quarkiverse.jsonrpc.deployment.openrpc;

import java.util.HashSet;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.openrpc.OpenRpcDocument;
import io.quarkiverse.jsonrpc.runtime.JsonRPCRecorder;
import io.quarkiverse.jsonrpc.runtime.config.JsonRpcConfig;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.SystemPropertyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class OpenRpcProcessor {
    private static final Logger LOG = Logger.getLogger(OpenRpcProcessor.class);


//    @BuildStep
//    public void buildSchemaDocument(
//            JsonRpcConfig jsonRpcConfig,
//            BuildProducer<OpenRpcDocumentBuildItem> openRpcDocumentProducer) {
//        OpenRpcDocument document = createDocument(jsonRpcConfig);
//        openRpcDocumentProducer.produce(new OpenRpcDocumentBuildItem(document));
//    }

//    @Record(ExecutionTime.RUNTIME_INIT)
//    @BuildStep
//    void buildSchemaEndpoint(
//            BuildProducer<RouteBuildItem> routeProducer,
//            HttpRootPathBuildItem httpRootPathBuildItem,
//            OpenRpcDocumentInitializedBuildItem openRpcDocumentInitializedBuildItem,
//            OpenRpcDocumentBuildItem openRpcDocumentBuildItem,
//            JsonRPCRecorder recorder,
//            JsonRpcConfig jsonRpcConfig) {
//        Handler<RoutingContext> schemaHandler = recorder.schemaHandler(
//                openRpcDocumentInitializedBuildItem.getInitialized(), jsonRpcConfig.openRPC.schemaAvailable);
//
//        routeProducer.produce(httpRootPathBuildItem.routeBuilder()
//                .nestedRoute(jsonRpcConfig.openRPC.basePath, jsonRpcConfig.openRPC.schemaPath)
//                .handler(schemaHandler)
//                .displayOnNotFoundPage("JsonRPC OpenRPC Schema")
//                .build());
//    }

    private OpenRpcDocument createDocument(JsonRpcConfig jsonRpcConfig) {
        OpenRpcDocument document = OpenRpcDocument.builder().build();
        LOG.info("createDocument: " + document);
        return document;
    }
}
