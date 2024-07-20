package io.quarkiverse.jsonrpc.runtime.model;

import java.util.Objects;

public final class JsonRPCMethodName {

    private String name;
    private String orderedParameterKey = null;

    public JsonRPCMethodName() {
    }

    public JsonRPCMethodName(String name, String orderedParameterKey) {
        this.name = name;
        this.orderedParameterKey = orderedParameterKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean hasOrderedParameterKey() {
        return orderedParameterKey != null;
    }

    public String getOrderedParameterKey() {
        return orderedParameterKey;
    }

    public void setOrderedParameterKey(String orderedParameterKey) {
        this.orderedParameterKey = orderedParameterKey;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JsonRPCMethodName other = (JsonRPCMethodName) obj;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public String toString() {
        return name;
    }

}
