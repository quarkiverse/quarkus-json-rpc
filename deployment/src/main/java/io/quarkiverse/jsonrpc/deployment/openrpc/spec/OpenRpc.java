package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// TODO: Extract OpenRPC spec to its own library
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class OpenRpc {

    private final OpenRpcSpecVersion openrpc;

    public Info getInfo() {
        return info;
    }

    public Set<Server> getServers() {
        return servers;
    }

    public Set<Method> getMethods() {
        return methods;
    }

    private final Info info;
    private final Set<Server> servers;
    private final Set<Method> methods;

    public OpenRpc(
            OpenRpcSpecVersion openrpc,
            Info info,
            Set<Server> servers,
            Set<Method> methods
    //components
    //externalDocs
    ) {
        this.openrpc = openrpc;
        this.info = info;
        this.servers = servers;
        this.methods = methods;
    }

    @JsonProperty("openrpc")
    public String getOpenrpc() {
        return openrpc.value();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (OpenRpc) obj;
        return Objects.equals(this.openrpc, that.openrpc) &&
                Objects.equals(this.info, that.info) &&
                Objects.equals(this.servers, that.servers) &&
                Objects.equals(this.methods, that.methods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openrpc, info, servers, methods);
    }

    @Override
    public String toString() {
        return "OpenRpc[" +
                "openrpc=" + openrpc + ", " +
                "info=" + info + ", " +
                "servers=" + servers + ", " +
                "methods=" + methods + ']';
    }

}
