package io.quarkiverse.jsonrpc.app;

import java.util.UUID;

public class Pojo2 {

    public long id;
    public String desc;
    public UUID ref;

    public Pojo2(long id, String desc, UUID ref) {
        this.id = id;
        this.desc = desc;
        this.ref = ref;
    }
}
