# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Quarkus extension providing JSON-RPC 2.0 protocol support over WebSocket. Classes annotated with `@JsonRPCApi` are discovered at build time and their public methods become callable via JSON-RPC over a WebSocket endpoint.

## Build Commands

```bash
# Full build (includes tests, integration tests, docs)
mvn clean install

# Build without formatting checks (CI style)
mvn -B clean install -Dno-format

# Build specific module
mvn clean install -pl deployment    # or runtime, sample, integration-tests

# Run a single test
mvn test -pl deployment -Dtest=NormalJsonRpcTest

# Native image build (sample module)
mvn clean install -Dnative -pl sample
```

Java 17 required. The compiler uses `-parameters` flag for reflection-based parameter name discovery.

## Module Structure

- **runtime** (`quarkus-json-rpc`) - Runtime extension code: router, WebSocket handler, codec, request/response models
- **deployment** (`quarkus-json-rpc-deployment`) - Build-time processing: annotation scanning, bean registration, native image config
- **sample** - Example application demonstrating extension usage
- **integration-tests** - Integration tests with resteasy-reactive (activated by default, skipped during release)
- **docs** - Antora documentation

## Architecture

This follows the standard Quarkus extension split:

**Build time (`deployment/`):**
- `JsonRPCProcessor` scans the Jandex index for `@JsonRPCApi`-annotated classes
- Collects public methods with non-void returns as JSON-RPC endpoints
- Creates method lookup keys in the format `Scope#methodName(param1,param2)`
- Registers synthetic beans (`JsonRPCRouter`, `JsonRPCWebSocket`) via ArC
- Registers a Vert.x HTTP route for WebSocket upgrade at the configured path
- Registers classes for native image reflection

**Runtime (`runtime/`):**
- `JsonRPCRouter` - Receives JSON-RPC requests, resolves the target method via reflection, invokes it
- `JsonRPCWebSocket` - Vert.x `RoutingContext` handler that upgrades HTTP to WebSocket
- `JsonRPCCodec` - Jackson-based serialization/deserialization of JSON-RPC messages
- `JsonRPCRecorder` - Quarkus recorder creating runtime beans

**Method dispatch rules:**
- Plain return type: blocking by default (runs on worker thread via `vertx.executeBlocking()`)
- `@NonBlocking`: forces execution on the event loop
- `@Blocking`: forces execution on worker thread (explicit)
- `Uni<T>` return: async, non-blocking by default; `@Blocking` wraps in `executeBlocking()`
- `Multi<T>` return: streaming subscription with automatic cancellation tracking

**Configuration** (build-time):
- `quarkus.json-rpc.web-socket.enabled` (default: `true`)
- `quarkus.json-rpc.web-socket.path` (default: `/quarkus/json-rpc`)

## Testing Patterns

Tests live in `deployment/src/test/java/` and use `QuarkusUnitTest` with Vert.x `WebSocketClient`.

- `JsonRpcParent` is the base class providing WebSocket client utilities (`getJsonRpcResponse()` methods)
- Tests register application classes via `withApplicationRoot()` and send JSON-RPC messages over WebSocket
- Named params: `getJsonRpcResponse("Scope#method", Map.of("key", "value"))`
- Positional params: `getJsonRpcResponse("Scope#method", new String[]{"value"})`
- Test app classes (e.g., `HelloResource`, `PojoResource`) are in the `io.quarkiverse.jsonrpc.app` package under `deployment/src/test/java/`

## Key Packages

- `io.quarkiverse.jsonrpc.api` - Public API (`@JsonRPCApi` annotation)
- `io.quarkiverse.jsonrpc.runtime` - Runtime implementation
- `io.quarkiverse.jsonrpc.runtime.model` - Request/response/method model classes
- `io.quarkiverse.jsonrpc.deployment` - Build-time processors
- `io.quarkiverse.jsonrpc.deployment.config` - Build-time configuration classes
