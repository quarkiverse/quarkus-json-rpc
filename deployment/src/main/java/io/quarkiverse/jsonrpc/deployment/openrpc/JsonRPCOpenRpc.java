package io.quarkiverse.jsonrpc.deployment.openrpc;

import io.quarkiverse.jsonrpc.deployment.openrpc.spec.*;

import java.util.HashSet;
import java.util.Set;

//TODO: Full OpenRPC Spec build based on passed in arguments
public class JsonRPCOpenRpc {

    private final OpenRpc model;

    private JsonRPCOpenRpc(OpenRpc model) {
        this.model = model;
    }

    public OpenRpc model() {
        return model;
    }

    public static JsonRPCOpenRpc.Builder builder() {
        return new JsonRPCOpenRpc.Builder();
    }

    public static class Builder {
        private Builder() {
        }

        public JsonRPCOpenRpc build() {
            OpenRpcSpecVersion openRPCSpecVersion = OpenRpcSpecVersion._1_3_2;
            Contact contact = new Contact("Alexander Haslam", "https://indiealex.com", "alex@indiealexh.com");
            License license = new License("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt");
            Info info = new Info(
                    "example",
                    "example",
                    "tos",
                    contact,
                    license,
                    "1.0.0");
            Set<Server> servers = new HashSet<>();
            Set<Method> methods = new HashSet<>();

            OpenRpc openRPCModel = new OpenRpc(
                    openRPCSpecVersion,
                    info,
                    servers,
                    methods);
            return new JsonRPCOpenRpc(openRPCModel);
        }

    }
}
