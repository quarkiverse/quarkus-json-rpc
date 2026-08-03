# JSON-RPC Domain Socket Sample

Sample demonstrating JSON-RPC 2.0 over a Unix domain socket using JSONL framing (newline-delimited JSON).

## Running

```bash
mvn quarkus:dev -pl sample/domain-socket
```

The server creates a Unix domain socket at `/tmp/quarkus-json-rpc-sample.sock`.

## Testing with socat

The `sleep` in the subshell keeps the connection open while the server processes the request:

```bash
(printf '{"jsonrpc":"2.0","id":1,"method":"GreetingResource#greet"}\n'; sleep 3) \
  | socat - UNIX-CONNECT:/tmp/quarkus-json-rpc-sample.sock
```

With parameters:

```bash
(printf '{"jsonrpc":"2.0","id":2,"method":"GreetingResource#greet","params":{"name":"World"}}\n'; sleep 3) \
  | socat - UNIX-CONNECT:/tmp/quarkus-json-rpc-sample.sock
```

Async method (Uni):

```bash
(printf '{"jsonrpc":"2.0","id":3,"method":"GreetingResource#greetAsync","params":{"name":"Quarkus"}}\n'; sleep 3) \
  | socat - UNIX-CONNECT:/tmp/quarkus-json-rpc-sample.sock
```

Streaming (Multi) - returns an ack with a subscription ID followed by items and a completion notification:

```bash
(printf '{"jsonrpc":"2.0","id":4,"method":"GreetingResource#greetStream","params":{"name":"Stream"}}\n'; sleep 3) \
  | socat - UNIX-CONNECT:/tmp/quarkus-json-rpc-sample.sock
```

## Protocol

Each JSON-RPC message is a single line terminated by `\n`. Responses follow the same framing. This is sometimes called JSONL or newline-delimited JSON (NDJSON).

## Available methods

| Method | Params | Returns |
|--------|--------|---------|
| `GreetingResource#greet` | none | String |
| `GreetingResource#greet` | `name` (String) | String |
| `GreetingResource#greetAsync` | `name` (String) | Uni (async String) |
| `GreetingResource#greetStream` | `name` (String) | Multi (3 streamed items) |
