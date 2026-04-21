package io.quarkiverse.jsonrpc.runtime;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.vertx.core.Promise;

/**
 * Isolates the {@code quarkus-virtual-threads} dependency so that
 * {@link JsonRPCRouter} can be loaded even when the extension is absent.
 * This class is only referenced when a method is annotated with
 * {@code @RunOnVirtualThread}, which requires the extension at build time.
 */
final class VirtualThreadSupport {

    private VirtualThreadSupport() {
    }

    @SuppressWarnings("unchecked")
    static void executeBlocking(Callable<Object> callable, Promise<?> result) {
        try {
            ExecutorService executor = VirtualThreadsRecorder.getCurrent();
            executor.submit(() -> {
                try {
                    ((Promise<Object>) result).complete(callable.call());
                } catch (Throwable e) {
                    result.fail(e);
                }
            });
        } catch (Exception e) {
            result.fail(e);
        }
    }
}
