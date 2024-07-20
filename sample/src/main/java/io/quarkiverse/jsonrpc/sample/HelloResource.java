package io.quarkiverse.jsonrpc.sample;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;

@JsonRPCApi
public class HelloResource {

    public String hello() {
        return "Hello";
    }

    public String hello(String name) {
        return "Hello " + name;
    }

    public String hello(String name, String surname) {
        return "Hello " + name + " " + surname;
    }
}
