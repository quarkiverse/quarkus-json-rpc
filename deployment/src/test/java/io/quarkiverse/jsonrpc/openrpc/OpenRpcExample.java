package io.quarkiverse.jsonrpc.openrpc;

import java.util.HashSet;
import java.util.Set;

import io.quarkiverse.jsonrpc.deployment.openrpc.spec.*;

public class OpenRpcExample {
    OpenRpcExample() {
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

        OpenRpc openRPC = new OpenRpc(
                openRPCSpecVersion,
                info,
                servers,
                methods);
    }
}
