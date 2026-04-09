package io.quarkiverse.jsonrpc.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Uni;

@JsonRPCApi
public class CollectionResource {

    // --- Return type tests ---

    public List<String> listOfStrings() {
        return List.of("alpha", "beta", "gamma");
    }

    public List<Pojo> listOfPojos() {
        return List.of(createPojo("Alice"), createPojo("Bob"));
    }

    public Map<String, String> mapOfStrings() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
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

    public String[] arrayOfStrings() {
        return new String[] { "x", "y", "z" };
    }

    // --- Uni-wrapped return type tests ---

    public Uni<List<String>> uniListOfStrings() {
        return Uni.createFrom().item(listOfStrings());
    }

    public Uni<Map<String, Pojo>> uniMapOfPojos() {
        return Uni.createFrom().item(mapOfPojos());
    }

    // --- Parameter deserialization tests ---

    public String joinStrings(List<String> items) {
        return String.join(",", items);
    }

    public int countPojos(List<Pojo> pojos) {
        return pojos.size();
    }

    public String lookupInMap(Map<String, String> data, String key) {
        return data.get(key);
    }

    public String lookupPojoName(Map<String, Pojo> data, String key) {
        Pojo p = data.get(key);
        return p != null ? p.getName() : null;
    }

    public int arrayLength(String[] items) {
        return items.length;
    }

    private Pojo createPojo(String name) {
        Pojo pojo = new Pojo();
        pojo.setName(name);
        pojo.setSurname("Test");
        pojo.setThread(Thread.currentThread().getName());
        pojo.setPojo2(new Pojo2(0, "desc", UUID.randomUUID()));
        return pojo;
    }
}
