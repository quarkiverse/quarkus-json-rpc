package io.quarkiverse.jsonrpc.runtime;

import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class JsonRPCHotReplacementSetup implements HotReplacementSetup {

    private static volatile HotReplacementContext context;

    private static final long HOT_REPLACEMENT_INTERVAL = 2000L;
    private static volatile long nextUpdate;

    @Override
    public void setupHotDeployment(HotReplacementContext ctx) {
        context = ctx;
    }

    static boolean scan() {
        HotReplacementContext ctx = context;
        if (ctx == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (nextUpdate > now) {
            return false;
        }
        synchronized (JsonRPCHotReplacementSetup.class) {
            if (nextUpdate > now) {
                return false;
            }
            nextUpdate = now + HOT_REPLACEMENT_INTERVAL;
            try {
                return ctx.doScan(true);
            } catch (Exception e) {
                throw new RuntimeException("JSON-RPC hot reload scan failed", e);
            }
        }
    }
}
