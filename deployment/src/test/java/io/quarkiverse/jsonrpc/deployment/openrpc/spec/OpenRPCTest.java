package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wildfly.common.Assert;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

class OpenRPCTest {

    @Inject
    Vertx vertx;

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest();

    @Test
    public void testOpenRPCEncode() {

        OpenRPCSpecVersion openRPCSpecVersion = OpenRPCSpecVersion._1_3_2;
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

        OpenRPC openRPC = new OpenRPC(
                openRPCSpecVersion,
                info,
                servers,
                methods);

        JsonObject jsonObject = JsonObject.mapFrom(openRPC);
        System.out.println(jsonObject.encodePrettily());
        Assert.assertTrue(jsonObject.containsKey("openrpc"));

    }

}
