package io.quarkiverse.jsonrpc.runtime.config;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.WithName;

/**
 * JsonRPC Configuration
 */
@ConfigRoot(prefix = "quarkus", name = "json-rpc", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class JsonRpcConfig {

    /**
     * Configuration properties for the JsonRPC Websocket
     */
    @ConfigItem(name = "web-socket")
    public JsonRPCWebSocketConfig webSocket;

    /**
     * Configuration properties for the OpenRPC Schema document and DevUIs
     */
    @ConfigItem(name = "open-rpc")
    public JsonRPCOpenRpcConfig openRpc;

    /**
     * OpenRPC Config
     */
    @ConfigGroup
    public static class JsonRPCOpenRpcConfig {
        /**
         * Enable JsonRPC OpenRPC Spec
         */
        @ConfigItem(defaultValue = "true")
        public boolean enabled;

        /**
         * HTTP Path for the JsonRPC OpenRPC DevUI
         */
        @ConfigItem(defaultValue = "open-rpc")
        public String basePath;

        /**
         * HTTP Path for the JsonRPC OpenRPC Playground
         */
        @ConfigItem(defaultValue = "playground")
        public String playgroundPath;

        /**
         * HTTP Path for the JsonRPC OpenRPC Spec file
         */
        @ConfigItem(defaultValue = "openrpc.json")
        public String schemaPath;

        /**
         * If the OpenRPC Schema file should be accessible
         */
        @ConfigItem(defaultValue = "true")
        boolean schemaAvailable;

        /**
         * Location to store the generated OpenRPC Schema file
         */
        @ConfigItem(defaultValue = "/open-rpc")
        public Path storeSchemaDirectory;

        /**
         * OpenRPC Schema Config
         *
         * @return OpenRPC Schema Config
         */
        @ConfigItem(name = "schema")
        public JsonRPCOpenRpcSchemaConfig schema;

        /**
         * Open RPC Schema Config
         */
        @ConfigGroup
        public static class JsonRPCOpenRpcSchemaConfig {

            /**
             * OpenRPC Schema Spec version
             */
            @ConfigItem(defaultValue = "1.2.6")
            String version;

            /**
             * OpenRPC Schema Application Info
             */
            @ConfigItem(name = "info")
            public JsonRPCOpenRpcSchemaInfoConfig info;

            /**
             * OpenRPC Schema Config - Info
             */
            @ConfigGroup
            public static class JsonRPCOpenRpcSchemaInfoConfig {

                /**
                 * Application Title
                 */
                @ConfigItem(defaultValue = "JsonRPC API")
                String title;

                /**
                 * Application Description
                 */
                @ConfigItem(defaultValue = "A JsonRPC API")
                String description;

                /**
                 * Terms Of Service URL
                 */
                @ConfigItem(name = "terms-of-service")
                Optional<String> termsOfService;

                /**
                 * Application Owner Contact Information
                 */
                @ConfigItem(name = "contact")
                public Optional<JsonRPCOpenRpcSchemaContactConfig> contact;

                /**
                 * API License
                 */
                @WithName("license")
                public Optional<JsonRPCOpenRpcSchemaLicenseConfig> license;

                /**
                 * Application version
                 */
                @ConfigItem(defaultValue = "0.0.0")
                public String version;

                /**
                 * OpenRPC Schema Config - Contact
                 */
                @ConfigGroup
                public static class JsonRPCOpenRpcSchemaContactConfig {

                    /**
                     * Name of the contact
                     */
                    @ConfigItem(name = "name")
                    public Optional<String> name;

                    /**
                     * URL to the contact
                     */
                    @ConfigItem(name = "url")
                    public Optional<String> url;

                    /**
                     * Email of the contact
                     */
                    @ConfigItem(name = "email")
                    public Optional<String> email;
                }

                /**
                 * OpenRPC Schema Config - License
                 */
                @ConfigGroup
                public static class JsonRPCOpenRpcSchemaLicenseConfig {

                    /**
                     * Name of License
                     */
                    @ConfigItem(name = "name")
                    public Optional<String> name;

                    /**
                     * URL of the License
                     */
                    @ConfigItem(name = "url")
                    public Optional<String> url;
                }
            }

        }

    }

    /**
     * Web Socket Config
     */
    @ConfigGroup
    public static class JsonRPCWebSocketConfig {

        /**
         * Enable JsonRPC Websocket
         */
        @ConfigItem(defaultValue = "true")
        public boolean enabled;

        /**
         * HTTP Path for the JsonRPC Websocket
         */
        @ConfigItem(defaultValue = "quarkus/json-rpc")
        public String path;
    }

}
