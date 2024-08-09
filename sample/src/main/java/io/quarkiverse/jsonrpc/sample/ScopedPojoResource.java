package io.quarkiverse.jsonrpc.sample;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;
import io.quarkiverse.jsonrpc.sample.model.Pojo;
import io.quarkiverse.jsonrpc.sample.model.Pojo2;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@JsonRPCApi("scoped")
public class ScopedPojoResource {

    public Pojo pojo() {
        return createPojo();
    }

    public Pojo pojo(String name) {
        return createPojo(name);
    }

    public Pojo pojo(String name, String surname) {
        return createPojo(name, surname);
    }

    public Pojo parrot(Pojo pojo) {
        return pojo;
    }

    public Uni<Pojo> pojoUni() {
        return Uni.createFrom().item(pojo());
    }

    public Uni<Pojo> pojoUni(String name) {
        return Uni.createFrom().item(pojo(name));
    }

    public Uni<Pojo> pojoUni(String name, String surname) {
        return Uni.createFrom().item(pojo(name, surname));
    }

    public Uni<Pojo> parrotUni(Pojo pojo) {
        return Uni.createFrom().item(parrot(pojo));
    }

    public Multi<Pojo> pojoMulti() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(n -> createPojo(null, null, n));
    }

    public Multi<Pojo> pojoMulti(String name) {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(n -> createPojo(name, null, n));
    }

    public Multi<Pojo> pojoMulti(String name, String surname) {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(n -> createPojo(name, surname, n));
    }

    public Multi<Pojo> parrotMulti(Pojo pojo) {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .onItem().transform(n -> pojo);
    }

    private Pojo createPojo() {
        return createPojo(null, null, 0L);
    }

    private Pojo createPojo(String name) {
        return createPojo(name, null, 0L);
    }

    private Pojo createPojo(String name, String surname) {
        return createPojo(name, surname, 0L);
    }

    private Pojo createPojo(String name, String surname, long count) {
        Pojo pojo = new Pojo();
        if (name == null)
            name = "Koos";
        if (surname == null)
            surname = "van der Merwe";
        pojo.setName(name);
        pojo.setSurname(surname);
        pojo.setThread(Thread.currentThread().getName());
        pojo.setTime(LocalDateTime.now());
        pojo.setPojo2(new Pojo2(count, "Count", UUID.randomUUID()));
        return pojo;
    }
}
