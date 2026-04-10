package io.quarkiverse.jsonrpc.deployment;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;

public abstract class JsClientTestBase {

    @Inject
    Vertx vertx;

    @TestHTTPResource("/")
    URI baseUri;

    protected String httpGet(String path) throws Exception {
        HttpClient client = vertx.createHttpClient();
        try {
            CompletableFuture<String> future = new CompletableFuture<>();
            client.request(HttpMethod.GET, baseUri.getPort(), baseUri.getHost(), path)
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            ar.result().send().onComplete(respAr -> {
                                if (respAr.succeeded()) {
                                    var resp = respAr.result();
                                    if (resp.statusCode() != 200) {
                                        future.completeExceptionally(
                                                new AssertionError(
                                                        "Expected 200 for " + path + " but got " + resp.statusCode()));
                                        return;
                                    }
                                    resp.body().onComplete(bodyAr -> {
                                        if (bodyAr.succeeded()) {
                                            future.complete(bodyAr.result().toString());
                                        } else {
                                            future.completeExceptionally(bodyAr.cause());
                                        }
                                    });
                                } else {
                                    future.completeExceptionally(respAr.cause());
                                }
                            });
                        } else {
                            future.completeExceptionally(ar.cause());
                        }
                    });
            return future.get(10, TimeUnit.SECONDS);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    protected int httpStatus(String path) throws Exception {
        HttpClient client = vertx.createHttpClient();
        try {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            client.request(HttpMethod.GET, baseUri.getPort(), baseUri.getHost(), path)
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            ar.result().send().onComplete(respAr -> {
                                if (respAr.succeeded()) {
                                    future.complete(respAr.result().statusCode());
                                } else {
                                    future.completeExceptionally(respAr.cause());
                                }
                            });
                        } else {
                            future.completeExceptionally(ar.cause());
                        }
                    });
            return future.get(10, TimeUnit.SECONDS);
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }
    }
}
