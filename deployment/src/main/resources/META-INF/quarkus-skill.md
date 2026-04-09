### Creating a JSON-RPC Service

- Annotate any CDI bean with `@JsonRPCApi` to expose its public methods as JSON-RPC 2.0 endpoints over WebSocket.
- Use `@JsonRPCApi("customScope")` to set a custom scope name; otherwise the class simple name is used.
- Only public methods with non-void return types are exposed.

```java
@JsonRPCApi
public class GreetingService {
    public String hello(String name) {
        return "Hello " + name;
    }
}
```

### Supported Return Types

- Plain types (`String`, `int`, POJOs, etc.) — blocking by default, runs on worker thread.
- `Uni<T>` — async, non-blocking by default.
- `Multi<T>` — streaming subscription. Returns a subscription ID; items sent as notifications.
- `CompletionStage<T>` / `CompletableFuture<T>` — async, non-blocking by default.
- `Flow.Publisher<T>` — streaming, same behavior as `Multi<T>`.
- Collection types (`List`, `Set`, `Map`, `Optional`, arrays) are supported as both parameters and return types.

### Execution Control

- Default (no annotation): blocking, runs on Vert.x worker thread.
- `@NonBlocking`: forces execution on the event loop.
- `@Blocking`: forces execution on worker thread (explicit).
- For `Uni<T>`: non-blocking by default; `@Blocking` wraps in `executeBlocking()`.
- Do NOT annotate a method with both `@Blocking` and `@NonBlocking` — this causes a build error.

### Broadcasting Notifications

- Inject `JsonRPCBroadcaster` to push server-initiated notifications to connected clients.
- `broadcaster.broadcast("method", data)` sends to all clients.
- `broadcaster.send(sessionId, "method", data)` sends to a specific client.

### Method Invocation

- Named parameters: `{"method": "GreetingService#hello", "params": {"name": "World"}, "id": 1, "jsonrpc": "2.0"}`
- Positional parameters: `{"method": "GreetingService#hello", "params": ["World"], "id": 1, "jsonrpc": "2.0"}`
- Parameter names are resolved via the `-parameters` compiler flag.

### Common Pitfalls

- Do NOT create `@JsonRPCApi` beans manually with `new` — they are CDI-managed singletons.
- Ensure the `-parameters` compiler flag is set, otherwise parameter names will be `arg0`, `arg1`, etc.
- `Multi<T>` methods require clients to explicitly `unsubscribe` to cancel the stream.

### Testing

- Use `QuarkusUnitTest` with Vert.x `WebSocketClient` to test JSON-RPC methods.
- Connect to the WebSocket endpoint at the configured path (default: `/quarkus/json-rpc`).
- Send JSON-RPC 2.0 request messages and assert on the response.
