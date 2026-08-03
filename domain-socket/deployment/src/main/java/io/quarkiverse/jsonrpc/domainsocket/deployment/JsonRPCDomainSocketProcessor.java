package io.quarkiverse.jsonrpc.domainsocket.deployment;

import java.util.function.BooleanSupplier;

import io.quarkiverse.jsonrpc.domainsocket.deployment.config.JsonRPCDomainSocketBuildConfig;
import io.quarkiverse.jsonrpc.domainsocket.runtime.JsonRPCDomainSocketRecorder;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;

@BuildSteps(onlyIf = JsonRPCDomainSocketProcessor.DomainSocketEnabled.class)
public class JsonRPCDomainSocketProcessor {

    static class DomainSocketEnabled implements BooleanSupplier {
        JsonRPCDomainSocketBuildConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.domainSocket().enabled();
        }
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("json-rpc-domain-socket");
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerDomainSocketServer(
            JsonRPCDomainSocketBuildConfig config,
            JsonRPCDomainSocketRecorder recorder,
            BeanContainerBuildItem beanContainerBuildItem,
            ShutdownContextBuildItem shutdownContext) {
        recorder.startDomainSocketServer(
                beanContainerBuildItem.getValue(),
                config.domainSocket().path(),
                shutdownContext);
    }
}
