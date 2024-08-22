package io.quarkiverse.jsonrpc.deployment.openrpc.spec;

import java.util.HashSet;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.Vertx;

class OpenRpcTest {

    @Inject
    Vertx vertx;

    @Inject
    ObjectMapper objectMapper;

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest();

    @Test
    public void testOpenRpcEncode() throws JsonProcessingException {

        OpenRpcSpecVersion openRPCSpecVersion = OpenRpcSpecVersion._1_2_6;
        Contact contact = new Contact("Alexander Haslam", "https://indiealex.com", "alex@indiealexh.com");
        License license = new License("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt");
        Info info = new Info(
                "example",
                "example",
                "https://www.apache.org/licenses/LICENSE-2.0.txt",
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

        String jsonString = objectMapper.writeValueAsString(openRPC);

        //JsonObject jsonObject = JsonObject.mapFrom(openRPC);
        //String jsonString = jsonObject.encode();
        System.out.println(jsonString);
        //Assert.assertTrue(new JsonObject(jsonString).containsKey("openrpc"));

    }

}
