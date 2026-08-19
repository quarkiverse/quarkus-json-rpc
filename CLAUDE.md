# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Quarkus extension providing JSON-RPC 2.0 protocol support. Classes annotated with `@JsonRPCApi` are discovered at build time and their public methods become callable via JSON-RPC. The core is transport-agnostic; transports are separate modules (WebSocket and Unix domain socket).

## Build Commands

```bash
# Full build (includes tests, integration tests, docs)
mvn clean install

# Build without formatting checks (CI style)
mvn -B clean install -Dno-format

# Build specific module
mvn clean install -pl deployment    # or runtime, websocket, domain-socket, sample, integration-tests

# Run a single test (most tests live in the websocket module)
mvn test -pl websocket/deployment -Dtest=NormalJsonRpcTest

# Native image build (sample module)
mvn clean install -Dnative -pl sample
```

Java 17 required. The compiler uses `-parameters` flag for reflection-based parameter name discovery.

## Module Structure

- **runtime** (`quarkus-json-rpc`) - Transport-agnostic runtime core: router, codec, request/response models, `JsonRPCConnection` abstraction
- **deployment** (`quarkus-json-rpc-deployment`) - Build-time core: annotation scanning, bean registration, native image config
- **websocket** (`runtime` + `deployment`) - WebSocket transport: `JsonRPCWebSocket`, OpenRPC, JS client generation, WebSocket config. Most tests live here.
- **domain-socket** (`runtime` + `deployment`) - Unix domain socket transport
- **sample** - Example application demonstrating extension usage
- **rag** - RAG helper module
- **integration-tests** - Integration tests with resteasy-reactive (activated by default, skipped during release)
- **docs** - Antora documentation

## Architecture

This follows the standard Quarkus extension split:

**Build time (core `deployment/`):**
- `JsonRPCProcessor` scans the Jandex index for `@JsonRPCApi`-annotated classes
- Collects public methods with non-void returns as JSON-RPC endpoints
- Creates method lookup keys in the format `Scope#methodName(param1,param2)`
- Registers the synthetic `JsonRPCRouter` bean via ArC
- Registers classes for native image reflection

**Build time (`websocket/deployment/`):**
- `JsonRPCWebSocketProcessor` registers a Vert.x HTTP route for WebSocket upgrade at the configured path and the `JsonRPCWebSocket` bean

**Runtime (core `runtime/`, transport-agnostic):**
- `JsonRPCRouter` - Receives JSON-RPC requests, resolves the target method via reflection, invokes it, and writes responses through a `JsonRPCConnection`
- `JsonRPCConnection` - Transport abstraction implemented by each transport module
- `JsonRPCCodec` - Jackson-based serialization/deserialization of JSON-RPC messages
- `JsonRPCRecorder` - Quarkus recorder creating runtime beans

**Runtime (`websocket/runtime/`):**
- `JsonRPCWebSocket` - Vert.x `RoutingContext` handler that upgrades HTTP to WebSocket and adapts it to `JsonRPCConnection`

**Method dispatch rules:**
- Plain return type: blocking by default (runs on worker thread via `vertx.executeBlocking()`)
- `@NonBlocking`: forces execution on the event loop
- `@Blocking`: forces execution on worker thread (explicit)
- `Uni<T>` return: async, non-blocking by default; `@Blocking` wraps in `executeBlocking()`
- `Multi<T>` return: streaming subscription using JSON-RPC 2.0 notifications. Ack returns a subscription ID, items are sent as notifications with `method: "subscription"`, completion and error are signaled via notifications. Supports explicit `unsubscribe` by subscription ID

**Configuration** (build-time, defined in the `websocket` module):
- `quarkus.json-rpc.web-socket.enabled` (default: `true`)
- `quarkus.json-rpc.web-socket.path` (default: `/json-rpc`)

## Testing Patterns

Most tests live in `websocket/deployment/src/test/java/` and use `QuarkusUnitTest` with Vert.x `WebSocketClient`. (The core `deployment` module holds only build-time validation tests.)

- `JsonRpcParent` is the base class providing WebSocket client utilities (`getJsonRpcResponse()` methods)
- Tests register application classes via `withApplicationRoot()` and send JSON-RPC messages over WebSocket
- Named params: `getJsonRpcResponse("Scope#method", Map.of("key", "value"))`
- Positional params: `getJsonRpcResponse("Scope#method", new String[]{"value"})`
- Test app classes (e.g., `HelloResource`) are in the `io.quarkiverse.jsonrpc.app` package under `websocket/deployment/src/test/java/`

## Key Packages

- `io.quarkiverse.jsonrpc.api` - Public API (`@JsonRPCApi` annotation)
- `io.quarkiverse.jsonrpc.runtime` - Transport-agnostic runtime core (router, connection abstraction)
- `io.quarkiverse.jsonrpc.runtime.model` - Request/response/method model classes
- `io.quarkiverse.jsonrpc.deployment` - Build-time core processors
- `io.quarkiverse.jsonrpc.deployment.config` - Build-time configuration classes
- `io.quarkiverse.jsonrpc.websocket.runtime` / `.deployment` - WebSocket transport
- `io.quarkiverse.jsonrpc.domainsocket.runtime` / `.deployment` - Unix domain socket transport
