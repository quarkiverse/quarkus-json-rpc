package io.quarkiverse.jsonrpc.sample;

import java.util.logging.Logger;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi
public class EventResource {

    private static final Logger LOG = Logger.getLogger(EventResource.class.getName());

    public void trackEvent(String name) {
        LOG.info("Event tracked: " + name);
    }

    public void ping() {
        LOG.info("Ping received");
    }
}
