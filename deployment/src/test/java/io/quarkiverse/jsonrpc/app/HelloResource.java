package io.quarkiverse.jsonrpc.app;

import java.time.Duration;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@JsonRPCApi
public class HelloResource {

    public String hello() {
        return "Hello [" + Thread.currentThread().getName() + "]";
    }

    @NonBlocking
    public String helloNonBlocking() {
        return "Hello [" + Thread.currentThread().getName() + "]";
    }

    public String hello(String name) {
        return "Hello " + name + " [" + Thread.currentThread().getName() + "]";
    }

    @NonBlocking
    public String helloNonBlocking(String name) {
        return "Hello " + name + " [" + Thread.currentThread().getName() + "]";
    }

    public String hello(String name, String surname) {
        return "Hello " + name + " " + surname + " [" + Thread.currentThread().getName() + "]";
    }

    @NonBlocking
    public String helloNonBlocking(String name, String surname) {
        return "Hello " + name + " " + surname + " [" + Thread.currentThread().getName() + "]";
    }

    public Uni<String> helloUni() {
        return Uni.createFrom().item(hello());
    }

    @Blocking
    public Uni<String> helloUniBlocking() {
        return Uni.createFrom().item(hello());
    }

    public Uni<String> helloUni(String name) {
        return Uni.createFrom().item(hello(name));
    }

    @Blocking
    public Uni<String> helloUniBlocking(String name) {
        return Uni.createFrom().item(hello(name));
    }

    public Uni<String> helloUni(String name, String surname) {
        return Uni.createFrom().item(hello(name, surname));
    }

    @Blocking
    public Uni<String> helloUniBlocking(String name, String surname) {
        return Uni.createFrom().item(hello(name, surname));
    }

    public Multi<String> helloMulti() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(n -> "(" + n + ") " + hello());
    }

    public Multi<String> helloMulti(String name) {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(n -> "(" + n + ") " + hello(name));
    }

    public Multi<String> helloMulti(String name, String surname) {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(n -> "(" + n + ") " + hello(name, surname));
    }
}
