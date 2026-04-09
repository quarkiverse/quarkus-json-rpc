package io.quarkiverse.jsonrpc.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.common.annotation.Blocking;

@JsonRPCApi
public class CompletionStageResource {

    public CompletionStage<String> greeting() {
        return CompletableFuture.completedFuture("Hello from CompletionStage [" + Thread.currentThread().getName() + "]");
    }

    public CompletionStage<String> greeting(String name) {
        return CompletableFuture.completedFuture(
                "Hello " + name + " from CompletionStage [" + Thread.currentThread().getName() + "]");
    }

    @Blocking
    public CompletionStage<String> greetingBlocking() {
        return CompletableFuture.completedFuture("Hello from CompletionStage [" + Thread.currentThread().getName() + "]");
    }

    public CompletableFuture<String> greetingFuture() {
        return CompletableFuture.completedFuture("Hello from CompletableFuture [" + Thread.currentThread().getName() + "]");
    }

    public CompletableFuture<String> greetingFuture(String name) {
        return CompletableFuture.completedFuture(
                "Hello " + name + " from CompletableFuture [" + Thread.currentThread().getName() + "]");
    }

    @Blocking
    public CompletableFuture<String> greetingFutureBlocking() {
        return CompletableFuture.completedFuture("Hello from CompletableFuture [" + Thread.currentThread().getName() + "]");
    }
}
