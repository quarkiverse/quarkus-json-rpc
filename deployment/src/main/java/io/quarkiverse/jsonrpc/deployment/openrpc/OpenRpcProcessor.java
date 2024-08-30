package io.quarkiverse.jsonrpc.deployment.openrpc;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.JsonRPCRecorder;
import io.quarkiverse.jsonrpc.runtime.OpenRpcDocument;
import io.quarkiverse.jsonrpc.runtime.config.JsonRpcConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class OpenRpcProcessor {
    private static final Logger LOG = Logger.getLogger(OpenRpcProcessor.class);

    private static final String FEATURE = "open-rpc";

    JsonRpcConfig jsonRpcConfig;

    //    @BuildStep
    //    FeatureBuildItem feature() {
    //        return new FeatureBuildItem(FEATURE);
    //    }

    //    @BuildStep(onlyIf = { OpenRpcEnabled.class, IsDevelopment.class })
    //    void generateOpenRpcSchemaDocument(
    //            JsonRpcConfig jsonRpcConfig) {
    //
    //        OpenRpcDocument document = createDocument(jsonRpcConfig);
    //        String path = jsonRpcConfig.openRpc().basePath() + "/" + jsonRpcConfig.openRpc().schemaPath();
    //        String json = mapper.valueToTree(document).toString();
    //
    //        documentResourceBuildItemProducer.produce(new GeneratedStaticResourceBuildItem(path, json.getBytes()));
    //
    //        LOG.info("generateOpenRpcSchemaDocument: " + document);
    //    }
    //
    //    //    @BuildStep
    //    //    public void buildSchemaDocument(
    //    //            JsonRpcConfig jsonRpcConfig,
    //    //            BuildProducer<OpenRpcDocumentBuildItem> openRpcDocumentProducer) {
    //    //        OpenRpcDocument document = createDocument(jsonRpcConfig);
    //    //        openRpcDocumentProducer.produce(new OpenRpcDocumentBuildItem(document));
    //    //    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void buildSchemaEndpoint(
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            BuildProducer<RouteBuildItem> routeProducer,
            JsonRpcConfig jsonRpcConfig,
            JsonRPCRecorder recorder) {

        OpenRpcDocument document = createDocument(jsonRpcConfig);
        recorder.storeOpenRpcSchema(
                document.model(),
                jsonRpcConfig);

        Handler<RoutingContext> schemaHandler = recorder.schemaHandler();

        routeProducer.produce(
                nonApplicationRootPathBuildItem.routeBuilder()
                        .nestedRoute(jsonRpcConfig.openRpc.basePath, jsonRpcConfig.openRpc.schemaPath)
                        .handler(schemaHandler)
                        .displayOnNotFoundPage("JsonRPC OpenRPC Schema")
                        .build());
    }

    private OpenRpcDocument createDocument(JsonRpcConfig jsonRpcConfig) {

        OpenRpcDocument document = OpenRpcDocument.builder()
                .withConfig(jsonRpcConfig)
                .build();
        LOG.info("createDocument: " + document);
        return document;
    }
}
