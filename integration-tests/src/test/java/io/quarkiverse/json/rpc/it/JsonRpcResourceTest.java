package io.quarkiverse.json.rpc.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JsonRpcResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/json-rpc")
                .then()
                .statusCode(200)
                .body(is("Hello json-rpc"));
    }
}
