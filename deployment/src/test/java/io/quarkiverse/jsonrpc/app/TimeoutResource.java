package io.quarkiverse.jsonrpc.app;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi
public class TimeoutResource {

    public String slow() throws InterruptedException {
        Thread.sleep(5000);
        return "done";
    }

    public String fast() {
        return "Hello " + Thread.currentThread().getName();
    }
}
