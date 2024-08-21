package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OpenRPCSpecVersion {

    _1_3_2("1.3.2"),
    _1_3_1("1.3.1"),
    _1_3_0("1.3.0"),
    _1_2_6("1.2.6"),
    _1_2_5("1.2.5"),
    _1_2_4("1.2.4"),
    _1_2_3("1.2.3"),
    _1_2_2("1.2.2"),
    _1_2_1("1.2.1"),
    _1_2_0("1.2.0"),
    _1_1_12("1.1.12"),
    _1_1_11("1.1.11"),
    _1_1_10("1.1.10"),
    _1_1_9("1.1.9"),
    _1_1_8("1.1.8"),
    _1_1_7("1.1.7"),
    _1_1_6("1.1.6"),
    _1_1_5("1.1.5"),
    _1_1_4("1.1.4"),
    _1_1_3("1.1.3"),
    _1_1_2("1.1.2"),
    _1_1_1("1.1.1"),
    _1_1_0("1.1.0"),
    _1_0_0("1.0.0"),
    _1_0_0_RC_1("1.0.0-rc1"),
    _1_0_0_RC_0("1.0.0-rc0");

    private final String value;
    private final static Map<String, OpenRPCSpecVersion> CONSTANTS = new HashMap<String, OpenRPCSpecVersion>();

    static {
        for (OpenRPCSpecVersion c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    OpenRPCSpecVersion(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    @JsonCreator
    public static OpenRPCSpecVersion fromValue(String value) {
        OpenRPCSpecVersion constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
