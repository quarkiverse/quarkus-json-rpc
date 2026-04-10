package io.quarkiverse.jsonrpc.deployment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jsonrpc.app.HelloResource;
import io.quarkiverse.jsonrpc.app.Pojo;
import io.quarkiverse.jsonrpc.app.Pojo2;
import io.quarkiverse.jsonrpc.app.PojoResource;
import io.quarkus.test.QuarkusUnitTest;

public class JsClientGenerationTest extends JsClientTestBase {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(HelloResource.class, PojoResource.class, Pojo.class, Pojo2.class);
            })
            .overrideConfigKey("quarkus.json-rpc.client.enabled", "true");

    @Test
    public void testClientLibraryIsServed() throws Exception {
        String body = httpGet("/_static/quarkus-json-rpc/jsonrpc-client.js");
        Assertions.assertTrue(body.contains("export class JsonRPCClient"),
                "Client library should contain JsonRPCClient class");
        Assertions.assertTrue(body.contains("export class Subscription"),
                "Client library should contain Subscription class");
    }

    @Test
    public void testTypedProxyIsServed() throws Exception {
        String body = httpGet("/_static/quarkus-json-rpc-api/jsonrpc-api.js");
        Assertions.assertTrue(body.contains("import { JsonRPCClient } from '/_static/quarkus-json-rpc/jsonrpc-client.js'"),
                "Proxy should import from absolute path");
        Assertions.assertTrue(body.contains("export const client = new JsonRPCClient({ path: '/quarkus/json-rpc' })"),
                "Proxy should export a client instance with configured path");
    }

    @Test
    public void testProxyContainsScopeExports() throws Exception {
        String body = httpGet("/_static/quarkus-json-rpc-api/jsonrpc-api.js");
        Assertions.assertTrue(body.contains("export const HelloResource"),
                "Proxy should export HelloResource scope");
        Assertions.assertTrue(body.contains("export const PojoResource"),
                "Proxy should export PojoResource scope");
    }

    @Test
    public void testProxyUsesCallForRegularMethods() throws Exception {
        String body = httpGet("/_static/quarkus-json-rpc-api/jsonrpc-api.js");
        Assertions.assertTrue(body.contains("hello: (params) => client.call('HelloResource#hello'"),
                "Regular methods should use client.call()");
        Assertions.assertTrue(body.contains("helloUni: (params) => client.call('HelloResource#helloUni'"),
                "Uni methods should use client.call()");
    }

    @Test
    public void testProxyUsesSubscribeForStreamingMethods() throws Exception {
        String body = httpGet("/_static/quarkus-json-rpc-api/jsonrpc-api.js");
        Assertions.assertTrue(body.contains("helloMulti: (params) => client.subscribe('HelloResource#helloMulti'"),
                "Multi methods should use client.subscribe()");
        Assertions.assertTrue(body.contains("pojoMulti: (params) => client.subscribe('PojoResource#pojoMulti'"),
                "Multi methods on PojoResource should use client.subscribe()");
    }
}
