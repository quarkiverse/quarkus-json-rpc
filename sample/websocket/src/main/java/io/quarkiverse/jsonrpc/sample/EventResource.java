package io.quarkiverse.jsonrpc.sample;

import org.jboss.logging.Logger;

import io.quarkiverse.jsonrpc.api.JsonRPCApi;

@JsonRPCApi
public class EventResource {

    private static final Logger LOG = Logger.getLogger(EventResource.class);

    public void trackEvent(String name) {
        LOG.infof("Event tracked: %s", name);
    }

    public void ping() {
        LOG.info("Ping received");
    }
}
