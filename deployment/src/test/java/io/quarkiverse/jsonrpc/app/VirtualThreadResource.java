package io.quarkiverse.jsonrpc.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;

@JsonRPCApi
public class VirtualThreadResource {

    @RunOnVirtualThread
    public String hello() {
        return "Hello [" + Thread.currentThread().getName() + "]";
    }

    @RunOnVirtualThread
    public String hello(String name) {
        return "Hello " + name + " [" + Thread.currentThread().getName() + "]";
    }

    @RunOnVirtualThread
    public Uni<String> helloUni() {
        return Uni.createFrom().item("Hello [" + Thread.currentThread().getName() + "]");
    }

    @RunOnVirtualThread
    public Uni<String> helloUni(String name) {
        return Uni.createFrom().item("Hello " + name + " [" + Thread.currentThread().getName() + "]");
    }

    @RunOnVirtualThread
    public CompletionStage<String> helloCompletionStage() {
        return CompletableFuture.completedFuture("Hello [" + Thread.currentThread().getName() + "]");
    }

    @RunOnVirtualThread
    public CompletionStage<String> helloCompletionStage(String name) {
        return CompletableFuture.completedFuture("Hello " + name + " [" + Thread.currentThread().getName() + "]");
    }

    @RunOnVirtualThread
    @Blocking
    public String helloBlocking() {
        return "Hello [" + Thread.currentThread().getName() + "]";
    }
}
