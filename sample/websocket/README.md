# JSON-RPC WebSocket Sample

Interactive browser-based sample demonstrating JSON-RPC 2.0 over WebSocket.

## Running

```bash
mvn quarkus:dev -pl sample/websocket
```

Open http://localhost:8080 in your browser. The UI lets you:

- Call JSON-RPC methods (blocking, non-blocking, reactive)
- Send requests with POJO parameters and receive POJO responses
- Work with collections, maps, and optionals
- Test secured methods (admin/admin or user/user)
- Fire-and-forget notifications
- Subscribe to streaming responses (Multi)

## Security

Two embedded users are configured for testing secured methods:

| User  | Password | Role  |
|-------|----------|-------|
| admin | admin    | admin |
| user  | user     | user  |

## What's demonstrated

| Resource | Features |
|----------|----------|
| HelloResource | Blocking, @NonBlocking, Uni, Multi, method overloading |
| PojoResource | Complex object params/returns, Uni/Multi with POJOs |
| CollectionResource | List, Map, Set, Optional as params and returns |
| CustomPathResource | Custom WebSocket path (`/custom-rpc`) |
| EventResource | Void (fire-and-forget) methods |
| SecuredResource | @RolesAllowed, @PermitAll on JSON-RPC methods |
| ScopedHelloResource | Named scope grouping (`@JsonRPCApi("scoped")`) |
| ScopedPojoResource | Multiple resources sharing a scope |
