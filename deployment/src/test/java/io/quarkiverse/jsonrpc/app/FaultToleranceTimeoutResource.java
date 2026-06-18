package io.quarkiverse.jsonrpc.app;

import org.eclipse.microprofile.faulttolerance.Timeout;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi
public class FaultToleranceTimeoutResource {

    @Timeout(1000)
    public String slow() throws InterruptedException {
        Thread.sleep(5000);
        return "done";
    }

    public String fast() {
        return "Hello " + Thread.currentThread().getName();
    }
}
