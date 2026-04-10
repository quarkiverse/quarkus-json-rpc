package io.quarkiverse.jsonrpc.app;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.quarkus.security.Authenticated;

@JsonRPCApi
@RolesAllowed("admin")
public class SecuredResource {

    public String adminOnly() {
        return "admin-secret";
    }

    public String adminData(String key) {
        return "admin:" + key;
    }

    @PermitAll
    public String publicInfo() {
        return "public-info";
    }

    @RolesAllowed("user")
    public String userInfo() {
        return "user-info";
    }

    @DenyAll
    public String denied() {
        return "should-never-be-returned";
    }

    @Authenticated
    public String authenticatedOnly() {
        return "authenticated-info";
    }
}
