package io.quarkiverse.jsonrpc.sample;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

/**
 * Demonstrates security annotations on a JSON-RPC API.
 * <p>
 * Class-level {@code @RolesAllowed("admin")} requires the "admin" role by default.
 * Individual methods can override with {@code @PermitAll} or a different {@code @RolesAllowed}.
 */
@JsonRPCApi
@RolesAllowed("admin")
public class SecuredResource {

    /**
     * Only users with the "admin" role can call this.
     */
    public String adminSecret() {
        return "Top-secret admin data";
    }

    /**
     * Only users with the "admin" role can call this.
     */
    public String adminGreeting(String name) {
        return "Hello " + name + ", welcome to the admin area";
    }

    /**
     * Any authenticated user can call this (overrides class-level restriction).
     */
    @PermitAll
    public String publicInfo() {
        return "This information is available to everyone";
    }

    /**
     * Only users with the "user" role can call this (overrides class-level restriction).
     */
    @RolesAllowed("user")
    public String userDashboard() {
        return "Welcome to the user dashboard";
    }
}
