package io.quarkiverse.jsonrpc.domainsocket.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.runtime.JsonRPCRouter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.parsetools.RecordParser;

/**
 * A JSON-RPC server that listens on a Unix domain socket using JSONL framing
 * (one JSON-RPC message per line, newline-delimited).
 * <p>
 * Requires a native transport (epoll on Linux, kqueue on macOS) for domain socket support.
 * Add {@code io.netty:netty-transport-native-epoll} (Linux) or
 * {@code io.netty:netty-transport-native-kqueue} (macOS) to your dependencies.
 */
public class JsonRPCSocketServer {
    private static final Logger LOG = Logger.getLogger(JsonRPCSocketServer.class);

    private final JsonRPCRouter router;
    private final String socketPath;
    private volatile Vertx vertx;
    private volatile NetServer server;

    public JsonRPCSocketServer(JsonRPCRouter router, String socketPath) {
        this.router = router;
        this.socketPath = socketPath;
    }

    public void start() {
        deleteSocketFile();

        // Domain sockets require native transport (epoll/kqueue).
        // Create a dedicated Vertx instance with preferNativeTransport enabled.
        vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
        if (!vertx.isNativeTransportEnabled()) {
            LOG.errorf("Native transport is not available. Domain socket server cannot start. "
                    + "Add io.netty:netty-transport-native-epoll (Linux) or "
                    + "io.netty:netty-transport-native-kqueue (macOS) to your dependencies.");
            vertx.close();
            vertx = null;
            return;
        }

        server = vertx.createNetServer(new NetServerOptions());
        server.connectHandler(netSocket -> {
            JsonlConnection connection = new JsonlConnection(netSocket);
            router.addConnection(connection, null);

            RecordParser parser = RecordParser.newDelimited("\n", buffer -> {
                String line = buffer.toString().trim();
                if (!line.isEmpty()) {
                    router.handleMessage(connection, line);
                }
            });
            netSocket.handler(parser);

            netSocket.closeHandler(v -> router.removeConnection(connection));

            netSocket.exceptionHandler(err -> {
                LOG.warnf(err, "Error on JSON-RPC domain socket connection");
                router.removeConnection(connection);
                netSocket.close();
            });
        });

        SocketAddress address = SocketAddress.domainSocketAddress(socketPath);
        CountDownLatch latch = new CountDownLatch(1);
        server.listen(address).onComplete(ar -> {
            if (ar.succeeded()) {
                LOG.infof("JSON-RPC domain socket server listening on %s", socketPath);
            } else {
                LOG.errorf(ar.cause(), "Failed to start JSON-RPC domain socket server on %s", socketPath);
            }
            latch.countDown();
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                LOG.error("Timed out waiting for JSON-RPC domain socket server to start");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while waiting for JSON-RPC domain socket server to start");
        }
    }

    public void stop() {
        if (server != null) {
            server.close().onComplete(ar -> {
                deleteSocketFile();
                if (ar.failed()) {
                    LOG.warnf(ar.cause(), "Error closing JSON-RPC domain socket server");
                }
                if (vertx != null) {
                    vertx.close();
                }
            });
        }
    }

    private void deleteSocketFile() {
        try {
            Files.deleteIfExists(Path.of(socketPath));
        } catch (IOException e) {
            LOG.debugf(e, "Could not delete socket file: %s", socketPath);
        }
    }
}
