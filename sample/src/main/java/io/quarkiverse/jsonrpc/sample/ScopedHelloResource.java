package io.quarkiverse.jsonrpc.sample;

import java.time.Duration;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@JsonRPCApi("scoped")
public class ScopedHelloResource {

    public String hello() {
        return "Hello scoped " + " [" + Thread.currentThread().getName() + "]";
    }

    public String hello(String name) {
        return "Hello scoped " + name + " [" + Thread.currentThread().getName() + "]";
    }

    public String hello(String name, String surname) {
        return "Hello scoped " + name + " " + surname + " [" + Thread.currentThread().getName() + "]";
    }

    public Uni<String> helloUni() {
        return Uni.createFrom().item(hello());
    }

    public Uni<String> helloUni(String name) {
        return Uni.createFrom().item(hello(name));
    }

    public Uni<String> helloUni(String name, String surname) {
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
