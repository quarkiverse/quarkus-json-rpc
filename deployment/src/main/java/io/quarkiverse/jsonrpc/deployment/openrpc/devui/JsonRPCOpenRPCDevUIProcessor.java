package io.quarkiverse.jsonrpc.deployment.openrpc.devui;

import io.quarkiverse.jsonrpc.deployment.config.JsonRPCConfig;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.ExternalPageBuilder;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.devui.spi.page.PageBuilder;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;

public class JsonRPCOpenRPCDevUIProcessor {

    JsonRPCConfig jsonRPCConfig;

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createCard(NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem) {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();

        // Generated OpenRPC Schema
        String openRpcSchemaPath = "/" + jsonRPCConfig.openRPC.specPath;
        PageBuilder<ExternalPageBuilder> schemaPage = Page.externalPageBuilder("OpenRPC Schema")
                .icon("font-awesome-solid:scroll")
                .url(openRpcSchemaPath, openRpcSchemaPath);

        // OpenRPC UI
        String uiPath = nonApplicationRootPathBuildItem.resolvePath(jsonRPCConfig.openRPC.playgroundPath);
        PageBuilder<ExternalPageBuilder> uiPage = Page.externalPageBuilder("OpenRPC Playground")
                .icon("font-awesome-solid:table-columns")
                .url(uiPath + "/index.html?embed=true", uiPath);

        cardPageBuildItem.addPage(schemaPage);
        cardPageBuildItem.addPage(uiPage);

        return cardPageBuildItem;
    }

}
