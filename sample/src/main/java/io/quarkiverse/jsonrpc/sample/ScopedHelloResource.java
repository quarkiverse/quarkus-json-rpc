package io.quarkiverse.jsonrpc.sample;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;

@JsonRPCApi("scoped")
public class ScopedHelloResource {

    public String hello() {
        return "Hello scoped";
    }

    public String hello(String name) {
        return "Hello scoped " + name;
    }

    public String hello(String name, String surname) {
        return "Hello scoped " + name + " " + surname;
    }
}
