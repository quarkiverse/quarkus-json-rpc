package io.quarkiverse.jsonrpc.domainsocket.runtime;

import io.quarkiverse.jsonrpc.runtime.JsonRPCRouter;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JsonRPCDomainSocketRecorder {

    public void startDomainSocketServer(BeanContainer beanContainer, String socketPath,
            ShutdownContext shutdownContext) {
        JsonRPCRouter router = beanContainer.beanInstance(JsonRPCRouter.class);
        JsonRPCSocketServer server = new JsonRPCSocketServer(router, socketPath);
        server.start();
        shutdownContext.addShutdownTask(server::stop);
    }
}
