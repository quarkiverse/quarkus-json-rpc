# Quarkus JSON-RPC

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.json-rpc/quarkus-json-rpc?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.json-rpc/quarkus-json-rpc-parent)

A Quarkus extension that provides [JSON-RPC 2.0](https://www.jsonrpc.org/specification) protocol support over WebSocket. Annotate your classes with `@JsonRPCApi` and their public methods become callable via JSON-RPC — no boilerplate, no manual routing.

## Get Started

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.quarkiverse.json-rpc</groupId>
    <artifactId>quarkus-json-rpc</artifactId>
    <version>${quarkus-json-rpc.version}</version>
</dependency>
```

### 2. Create your API

```java
import io.quarkiverse.jsonrpc.api.JsonRPCApi;
import io.smallrye.mutiny.Uni;

@JsonRPCApi
public class GreetingService {

    public String hello(String name) {
        return "Hello " + name;
    }

    public Uni<String> helloAsync(String name) {
        return Uni.createFrom().item("Hello " + name);
    }
}
```

### 3. Call it over WebSocket

Connect to `ws://localhost:8080/quarkus/json-rpc` and send:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "GreetingService#hello",
  "params": { "name": "World" }
}
```

## Features

- **JSON-RPC 2.0** — full protocol compliance including error codes and notifications
- **WebSocket transport** — bidirectional communication out of the box
- **Reactive** — `Uni<T>` for async results, `Multi<T>` for streaming subscriptions
- **Blocking & non-blocking** — control execution threads with `@Blocking` and `@NonBlocking`
- **Server push** — broadcast notifications to all clients or target specific sessions
- **Named & positional parameters** — both JSON object and array parameter styles
- **POJO support** — automatic Jackson serialization for complex types
- **JavaScript client** — optional generated typed proxy for calling endpoints from the browser
- **Native image** — full GraalVM native compilation support
- **Dev UI** — interactive method tester and endpoint browser

## Documentation

The full documentation is available at https://docs.quarkiverse.io/quarkus-json-rpc/dev/index.html.

## Samples

Browse the [sample application](sample/) for working examples covering all supported return types, parameter styles, scoped APIs, and streaming.
