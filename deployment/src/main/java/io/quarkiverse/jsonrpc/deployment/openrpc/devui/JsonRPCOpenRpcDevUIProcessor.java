package io.quarkiverse.jsonrpc.deployment.openrpc.devui;

import io.quarkiverse.jsonrpc.runtime.config.JsonRpcConfig;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.*;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;

public class JsonRPCOpenRpcDevUIProcessor {

    JsonRpcConfig jsonRPCConfig;

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createCard(
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem) {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();

        // Generated OpenRPC Schema
        String openRpcSchemaPath = nonApplicationRootPathBuildItem
                .resolvePath(jsonRPCConfig.openRpc.basePath + "/" + jsonRPCConfig.openRpc.schemaPath);
        PageBuilder<ExternalPageBuilder> schemaPage = Page.externalPageBuilder("OpenRPC Schema")
                .icon("font-awesome-solid:file-code")
                .url(openRpcSchemaPath, openRpcSchemaPath)
                .isJsonContent();

        // OpenRPC UI
        String uiPath = nonApplicationRootPathBuildItem.resolvePath(jsonRPCConfig.openRpc.playgroundPath);
        PageBuilder<QuteDataPageBuilder> uiPage = Page.quteDataPageBuilder("OpenRPC Playground")
                .icon("font-awesome-solid:signs-post")
                .templateLink("open-rpc-playground-demo.qute.html");

        cardPageBuildItem.addPage(schemaPage);
        cardPageBuildItem.addPage(uiPage);

        cardPageBuildItem.addBuildTimeData("schemaUrl", openRpcSchemaPath);

        return cardPageBuildItem;
    }

}
