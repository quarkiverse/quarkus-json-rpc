package io.quarkiverse.jsonrpc.runtime;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.jsonrpc.openrpc.schema.*;
import io.quarkiverse.jsonrpc.runtime.config.JsonRpcConfig;

//TODO: Full OpenRPC Spec build based on passed in arguments
public class OpenRpcDocument {

    private final OpenRpc schema;

    public OpenRpcDocument() {
        this.schema = null;
    }

    private OpenRpcDocument(OpenRpc schema) {
        this.schema = schema;
    }

    public OpenRpc model() {
        return schema;
    }

    public static OpenRpcDocument.Builder builder() {
        return new OpenRpcDocument.Builder();
    }

    public static class Builder {

        @ConfigProperty(name = "quarkus.application.name")
        String applicationName;

        private Optional<String> contactName = Optional.empty();
        private Optional<String> contactUrl = Optional.empty();
        private Optional<String> contactEmail = Optional.empty();

        private Optional<String> licenseName = Optional.empty();
        private Optional<String> licenseUrl = Optional.empty();

        private Optional<String> title = Optional.empty();

        private Builder() {
        }

        public OpenRpcDocument build() {
            OpenRpcSpecVersion openRPCSpecVersion = OpenRpcSpecVersion._1_3_2;
            Contact contact = new Contact(contactName.orElse(""), contactUrl.orElse(""), contactEmail.orElse(""));
            License license = new License(licenseName.orElse("Apache 2.0"),
                    licenseUrl.orElse("https://www.apache.org/licenses/LICENSE-2.0.txt"));
            Info info = new Info(
                    title.orElse(applicationName),
                    "example",
                    "tos",
                    contact,
                    license,
                    "1.0.0");
            Set<Server> servers = new HashSet<>();
            Set<Method> methods = new HashSet<>();

            OpenRpc openRPCModel = new OpenRpc(
                    openRPCSpecVersion.value(),
                    info,
                    servers,
                    methods);
            return new OpenRpcDocument(openRPCModel);
        }

        /**
         * Populate OpenRPC Schema Document with values from config
         *
         * @param jsonRpcConfig OpenRPC Schema specific config
         * @return The current Builder instance
         */
        public Builder withConfig(JsonRpcConfig jsonRpcConfig) {

            final var schemaConfig = jsonRpcConfig.openRpc.schema;
            // Populate Contact from config
            schemaConfig.info.contact.ifPresent(contact -> {
                contactName = contact.name;
                contactUrl = contact.url;
                contactEmail = contact.email;
            });

            // Populate License from config
            schemaConfig.info.license.ifPresent(license -> {
                licenseName = license.name;
                licenseUrl = license.url;
            });

            return this;
        }
    }

    @Override
    public String toString() {
        return "OpenRpcDocument{" +
                "schema=" + schema +
                '}';
    }
}
