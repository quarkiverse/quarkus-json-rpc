package io.quarkiverse.jsonrpc.sample;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.quarkiverse.jsonrpc.sample.model.Pojo;
import io.quarkiverse.jsonrpc.sample.model.Pojo2;

@JsonRPCApi
public class CollectionResource {

    public List<String> listOfStrings() {
        return List.of("alpha", "beta", "gamma");
    }

    public List<Pojo> listOfPojos() {
        return List.of(createPojo("Alice"), createPojo("Bob"));
    }

    public Map<String, String> mapOfStrings() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("theme", "dark");
        map.put("language", "en");
        return map;
    }

    public Map<String, Pojo> mapOfPojos() {
        Map<String, Pojo> map = new LinkedHashMap<>();
        map.put("alice", createPojo("Alice"));
        map.put("bob", createPojo("Bob"));
        return map;
    }

    public Set<String> setOfStrings() {
        return Set.of("one", "two", "three");
    }

    public Optional<String> optionalPresent() {
        return Optional.of("present-value");
    }

    public Optional<String> optionalEmpty() {
        return Optional.empty();
    }

    public String joinStrings(List<String> items) {
        return String.join(", ", items);
    }

    public String lookupInMap(Map<String, String> data, String key) {
        return data.get(key);
    }

    private Pojo createPojo(String name) {
        Pojo pojo = new Pojo();
        pojo.setName(name);
        pojo.setSurname("Sample");
        pojo.setThread(Thread.currentThread().getName());
        pojo.setTime(LocalDateTime.now());
        pojo.setPojo2(new Pojo2(0, "collection-sample", UUID.randomUUID()));
        return pojo;
    }
}
