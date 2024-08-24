package io.quarkiverse.jsonrpc.openrpc;

import java.util.HashSet;
import java.util.Set;

import io.quarkiverse.jsonrpc.openrpc.schema.*;

//TODO: Full OpenRPC Spec build based on passed in arguments
public class OpenRpcDocument {

    private final OpenRpc schema;

    public OpenRpcDocument() {
        this.schema = null;
    }

    private OpenRpcDocument(OpenRpc schema) {
        this.schema = schema;
    }

    public OpenRpc model() {
        return schema;
    }

    public static OpenRpcDocument.Builder builder() {
        return new OpenRpcDocument.Builder();
    }

    public static class Builder {
        private Builder() {
        }

        public OpenRpcDocument build() {
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
            return new OpenRpcDocument(openRPCModel);
        }

    }

    @Override
    public String toString() {
        return "OpenRpcDocument{" +
                "schema=" + schema +
                '}';
    }
}
