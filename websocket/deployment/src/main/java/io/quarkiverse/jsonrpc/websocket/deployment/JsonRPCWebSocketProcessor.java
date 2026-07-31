package io.quarkiverse.jsonrpc.websocket.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import io.quarkiverse.jsonrpc.deployment.JsonRPCMethodsBuildItem;
import io.quarkiverse.jsonrpc.deployment.OpenRPCDocumentGenerator;
import io.quarkiverse.jsonrpc.deployment.config.JsonRPCConfig;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkiverse.jsonrpc.websocket.deployment.config.JsonRPCWebSocketBuildConfig;
import io.quarkiverse.jsonrpc.websocket.runtime.JsonRPCWebSocketRecorder;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.util.IoUtil;
import io.quarkus.devui.spi.buildtime.BuildTimeActionBuildItem;
import io.quarkus.vertx.http.deployment.FilterBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;
import io.quarkus.vertx.http.deployment.spi.WebDependencyJarBuildItem;

@BuildSteps(onlyIf = JsonRPCWebSocketProcessor.IsWebSocketEnabled.class)
public class JsonRPCWebSocketProcessor {
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(
            JsonRPCWebSocketProcessor.class);
    private static final Pattern JS_IDENTIFIER = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*$");
    private static final Set<String> JS_RESERVED_WORDS = Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "new", "null", "return", "super",
            "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with",
            "yield", "let", "static", "implements", "interface", "package", "private", "protected",
            "public", "await");
    private static final String FEATURE = "json-rpc-websocket";

    public static class IsWebSocketEnabled implements BooleanSupplier {
        JsonRPCWebSocketBuildConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.webSocket().enabled();
        }
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configurePathMapping(
            JsonRPCWebSocketBuildConfig config,
            JsonRPCWebSocketRecorder recorder,
            JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem,
            BeanContainerBuildItem beanContainer,
            HttpRootPathBuildItem httpRootPathBuildItem) {
        recorder.configurePathMapping(beanContainer.getValue(),
                resolvedScopeToPath(jsonRPCMethodsBuildItem, httpRootPathBuildItem),
                httpRootPathBuildItem.resolvePath(config.webSocket().path()));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerHandlers(
            JsonRPCWebSocketBuildConfig config,
            JsonRPCWebSocketRecorder recorder,
            JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem,
            BuildProducer<RouteBuildItem> routeProducer,
            BeanContainerBuildItem beanContainerBuildItem,
            HttpRootPathBuildItem httpRootPathBuildItem) {
        // Default WebSocket route for JsonRPC comms
        routeProducer.produce(
                httpRootPathBuildItem.routeBuilder()
                        .route(config.webSocket().path())
                        .routeConfigKey("quarkus.json-rpc.web-socket.path")
                        .handler(recorder.webSocketHandler(beanContainerBuildItem.getValue()))
                        .build());

        // Extra routes from @JsonRPCApi(path = "...")
        String defaultPath = config.webSocket().path();
        for (String extraPath : jsonRPCMethodsBuildItem.getExtraPaths()) {
            if (!extraPath.equals(defaultPath)) {
                routeProducer.produce(
                        httpRootPathBuildItem.routeBuilder()
                                .route(extraPath)
                                .handler(recorder.webSocketHandler(beanContainerBuildItem.getValue()))
                                .build());
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerSubProtocolFilter(
            JsonRPCWebSocketBuildConfig config,
            JsonRPCWebSocketRecorder recorder,
            JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem,
            HttpRootPathBuildItem httpRootPathBuildItem,
            BuildProducer<FilterBuildItem> filterProducer) {
        Set<String> resolvedPaths = new LinkedHashSet<>();
        resolvedPaths.add(httpRootPathBuildItem.resolvePath(config.webSocket().path()));
        for (String extraPath : jsonRPCMethodsBuildItem.getExtraPaths()) {
            resolvedPaths.add(httpRootPathBuildItem.resolvePath(extraPath));
        }
        filterProducer.produce(new FilterBuildItem(recorder.subProtocolHandler(resolvedPaths), 300));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerOpenRPCEndpoint(
            JsonRPCConfig jsonRPCConfig,
            JsonRPCWebSocketRecorder recorder,
            JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<RouteBuildItem> routeProducer,
            HttpRootPathBuildItem httpRootPathBuildItem) {

        if (!jsonRPCConfig.openrpc().enabled()) {
            return;
        }

        Map<JsonRPCMethodName, JsonRPCMethod> allMethods = jsonRPCMethodsBuildItem.getMethodsMap();
        Map<String, String> scopeToPath = jsonRPCMethodsBuildItem.getScopeToPath();

        // Partition methods: default-path methods vs custom-path methods
        Map<JsonRPCMethodName, JsonRPCMethod> defaultMethods = new HashMap<>();
        Map<String, Map<JsonRPCMethodName, JsonRPCMethod>> perPathMethods = new HashMap<>();

        for (Map.Entry<JsonRPCMethodName, JsonRPCMethod> entry : allMethods.entrySet()) {
            String key = entry.getKey().getName();
            String scope = key.substring(0, key.indexOf('#'));
            String customPath = scopeToPath.get(scope);
            if (customPath != null) {
                perPathMethods.computeIfAbsent(customPath, k -> new HashMap<>())
                        .put(entry.getKey(), entry.getValue());
            } else {
                defaultMethods.put(entry.getKey(), entry.getValue());
            }
        }

        // Default path OpenRPC document
        OpenRPCDocumentGenerator defaultGenerator = new OpenRPCDocumentGenerator(
                combinedIndexBuildItem.getIndex(), jsonRPCConfig.openrpc().schemaSimpleNames(),
                jsonRPCConfig.openrpc().title(), jsonRPCConfig.openrpc().version());
        String defaultDoc = defaultGenerator.generate(defaultMethods);

        routeProducer.produce(
                httpRootPathBuildItem.routeBuilder()
                        .route(jsonRPCConfig.openrpc().path())
                        .routeConfigKey("quarkus.json-rpc.openrpc.path")
                        .handler(recorder.openRpcHandler(defaultDoc))
                        .build());

        // Per-path OpenRPC documents
        for (Map.Entry<String, Map<JsonRPCMethodName, JsonRPCMethod>> pathEntry : perPathMethods.entrySet()) {
            String customPath = pathEntry.getKey();
            String openrpcPath = customPath + "/openrpc.json";

            OpenRPCDocumentGenerator pathGenerator = new OpenRPCDocumentGenerator(
                    combinedIndexBuildItem.getIndex(), jsonRPCConfig.openrpc().schemaSimpleNames(),
                    jsonRPCConfig.openrpc().title(), jsonRPCConfig.openrpc().version());
            String pathDoc = pathGenerator.generate(pathEntry.getValue());

            routeProducer.produce(
                    httpRootPathBuildItem.routeBuilder()
                            .route(openrpcPath)
                            .handler(recorder.openRpcHandler(pathDoc))
                            .build());
        }
    }

    // JavaScript client proxy

    @BuildStep
    void generateJsClient(
            JsonRPCWebSocketBuildConfig config,
            JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer) {

        if (!config.jsClient().enabled()) {
            return;
        }

        // 1. Copy the static client library
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = tccl.getResourceAsStream("jsonrpc/jsonrpc-client.js")) {
            if (is == null) {
                throw new IllegalStateException("jsonrpc/jsonrpc-client.js not found on classpath");
            }
            staticResourceProducer.produce(
                    new GeneratedStaticResourceBuildItem(
                            "/_static/quarkus-json-rpc/jsonrpc-client.js",
                            IoUtil.readBytes(is)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // 2. Generate the typed proxy module
        Map<JsonRPCMethodName, JsonRPCMethod> methodsMap = jsonRPCMethodsBuildItem.getMethodsMap();
        String proxyJs = generateTypedProxy(methodsMap, config.webSocket().path(),
                jsonRPCMethodsBuildItem.getScopeToPath());
        staticResourceProducer.produce(
                new GeneratedStaticResourceBuildItem(
                        "/_static/quarkus-json-rpc-api/jsonrpc-api.js",
                        proxyJs.getBytes(StandardCharsets.UTF_8)));
    }

    private String generateTypedProxy(Map<JsonRPCMethodName, JsonRPCMethod> methodsMap, String wsPath,
            Map<String, String> scopeToPath) {
        StringBuilder js = new StringBuilder();
        js.append("import { JsonRPCClient } from '@quarkiverse/json-rpc';\n\n");
        js.append("export const client = new JsonRPCClient({ path: '").append(escapeJsString(wsPath))
                .append("', autoConnect: false });\n\n");

        // Create additional clients for custom paths
        Set<String> extraPaths = new TreeSet<>(scopeToPath.values());
        Map<String, String> pathToClientVar = new HashMap<>();
        for (String path : extraPaths) {
            String varName = pathToClientVar(path);
            pathToClientVar.put(path, varName);
            js.append("const ").append(varName).append(" = new JsonRPCClient({ path: '")
                    .append(escapeJsString(path)).append("', autoConnect: false });\n");
        }
        if (!extraPaths.isEmpty()) {
            js.append("\n");
        }

        // Group methods by scope and method name (sorted for deterministic output).
        // Multiple overloads of the same method name share one JS proxy entry - the server
        // resolves the correct overload based on parameters. We validate that all overloads
        // agree on their JS client method category (call / subscribe / notify).
        Map<String, Map<String, MethodEntry>> byScope = new TreeMap<>();
        for (Map.Entry<JsonRPCMethodName, JsonRPCMethod> entry : methodsMap.entrySet()) {
            String key = entry.getKey().getName();
            int hashIdx = key.indexOf('#');
            String scope = key.substring(0, hashIdx);
            validateJsIdentifier(scope, "@JsonRPCApi scope");
            String methodName = entry.getValue().getMethodName();
            validateJsIdentifier(methodName, "Method name '" + scope + "#" + methodName + "'");
            Map<String, MethodEntry> scopeMethods = byScope.computeIfAbsent(scope, k -> new TreeMap<>());
            MethodEntry existing = scopeMethods.get(methodName);
            if (existing != null) {
                String existingCategory = jsClientMethod(existing.method);
                String newCategory = jsClientMethod(entry.getValue());
                if (!existingCategory.equals(newCategory)) {
                    throw new IllegalArgumentException(
                            "Overloaded method '" + scope + "#" + methodName + "' has conflicting return types: "
                                    + "some overloads use " + existingCategory + "() while others use " + newCategory
                                    + "(). "
                                    + "The JavaScript client proxy cannot represent both under the same method name. "
                                    + "Rename one of the overloads or make all overloads return the same category.");
                }
            } else {
                scopeMethods.put(methodName, new MethodEntry(scope, methodName, entry.getValue()));
            }
        }

        for (Map.Entry<String, Map<String, MethodEntry>> scopeEntry : byScope.entrySet()) {
            String scope = scopeEntry.getKey();
            Map<String, MethodEntry> methods = scopeEntry.getValue();

            String scopePath = scopeToPath.get(scope);
            String clientVar = (scopePath != null) ? pathToClientVar.get(scopePath) : "client";

            js.append("export const ").append(scope).append(" = {\n");
            int i = 0;
            for (MethodEntry me : methods.values()) {
                String clientMethod = jsClientMethod(me.method);
                String methodKey = me.scope + "#" + me.methodName;
                js.append("    ").append(me.methodName)
                        .append(": (params) => ").append(clientVar).append(".").append(clientMethod)
                        .append("('").append(methodKey).append("', params)");
                if (i < methods.size() - 1) {
                    js.append(",");
                }
                js.append("\n");
                i++;
            }
            js.append("};\n\n");
        }

        return js.toString();
    }

    @BuildStep
    void registerWebDependency(
            JsonRPCWebSocketBuildConfig config,
            CurateOutcomeBuildItem curateOutcome,
            BuildProducer<WebDependencyJarBuildItem> webDependencyProducer) {
        if (config.jsClient().enabled()) {
            curateOutcome.getApplicationModel().getDependencies().stream()
                    .filter(dep -> dep.getGroupId().equals("io.quarkiverse.json-rpc")
                            && (dep.getArtifactId().equals("quarkus-json-rpc-websocket-deployment")
                                    || dep.getArtifactId().equals("quarkus-json-rpc-websocket")))
                    .findFirst()
                    .ifPresent(dep -> webDependencyProducer.produce(new WebDependencyJarBuildItem(
                            dep.getKey(),
                            dep.getResolvedPaths().getSinglePath(),
                            Map.of(
                                    "@quarkiverse/json-rpc", "/_static/quarkus-json-rpc/jsonrpc-client.js",
                                    "@quarkiverse/json-rpc/", "/_static/quarkus-json-rpc/",
                                    "@quarkiverse/json-rpc-api", "/_static/quarkus-json-rpc-api/jsonrpc-api.js"))));
        }
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void enableMessageLog(JsonRPCWebSocketRecorder recorder, BeanContainerBuildItem beanContainer) {
        recorder.enableMessageLog(beanContainer.getValue());
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    BuildTimeActionBuildItem createDevUIBuildTimeActions(JsonRPCWebSocketBuildConfig config) {
        BuildTimeActionBuildItem actions = new BuildTimeActionBuildItem();
        String path = config.webSocket().path();
        actions.actionBuilder()
                .methodName("getEndpointPath")
                .description("Get the configured WebSocket endpoint path for JSON-RPC connections")
                .function(params -> path)
                .enableMcpFuctionByDefault()
                .build();
        return actions;
    }

    // --- Helper methods ---

    private static final Set<String> STREAMING_TYPES = Set.of(
            "io.smallrye.mutiny.Multi",
            "java.util.concurrent.Flow$Publisher");

    private static final Set<String> NON_STREAMING_REACTIVE_TYPES = Set.of(
            "io.smallrye.mutiny.Uni",
            "java.util.concurrent.CompletionStage");

    private static final Set<String> REACTIVE_TYPES;
    static {
        Set<String> all = new java.util.HashSet<>(NON_STREAMING_REACTIVE_TYPES);
        all.addAll(STREAMING_TYPES);
        REACTIVE_TYPES = Set.copyOf(all);
    }

    private boolean isStreamingReturnType(JsonRPCMethod method) {
        int paramCount = method.hasParams() ? method.getParams().size() : 0;
        return isReturnTypeAssignableTo(method.getClazz(), method.getMethodName(), paramCount, STREAMING_TYPES);
    }

    private boolean isVoidReturnType(JsonRPCMethod method) {
        int paramCount = method.hasParams() ? method.getParams().size() : 0;
        java.lang.reflect.Method m = findMethod(method.getClazz(), method.getMethodName(), paramCount);
        return m != null && m.getReturnType() == void.class;
    }

    private java.lang.reflect.Method findMethod(Class<?> clazz, String methodName, int paramCount) {
        try {
            for (java.lang.reflect.Method m : clazz.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
        } catch (Exception e) {
            LOG.debugf(e, "Failed to inspect method %s.%s", clazz.getName(), methodName);
        }
        return null;
    }

    private String jsClientMethod(JsonRPCMethod method) {
        if (isStreamingReturnType(method)) {
            return "subscribe";
        } else if (isVoidReturnType(method)) {
            return "notify";
        } else {
            return "call";
        }
    }

    private record MethodEntry(String scope, String methodName, JsonRPCMethod method) {
    }

    private static String pathToClientVar(String path) {
        String stripped = path.startsWith("/") ? path.substring(1) : path;
        String[] segments = stripped.split("[/-]");
        StringBuilder sb = new StringBuilder("_");
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty()) {
                continue;
            }
            if (i == 0) {
                sb.append(seg);
            } else {
                sb.append(Character.toUpperCase(seg.charAt(0)));
                sb.append(seg.substring(1));
            }
        }
        return sb.toString();
    }

    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static void validateJsIdentifier(String name, String label) {
        if (!JS_IDENTIFIER.matcher(name).matches() || JS_RESERVED_WORDS.contains(name)) {
            throw new IllegalArgumentException(
                    label + " '" + name + "' is not a valid JavaScript identifier. "
                            + "Use a name that starts with a letter, underscore, or dollar sign, "
                            + "contains only letters, digits, underscores, or dollar signs, "
                            + "and is not a JavaScript reserved word.");
        }
    }

    /**
     * Check whether a method's return type is assignable to any of the given type names.
     *
     * @param paramCount number of parameters to match the correct overload, or -1 to match any
     */
    private boolean isReturnTypeAssignableTo(Class<?> clazz, String methodName, int paramCount, Set<String> typeNames) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            for (java.lang.reflect.Method m : clazz.getMethods()) {
                if (m.getName().equals(methodName)
                        && (paramCount < 0 || m.getParameterCount() == paramCount)) {
                    Class<?> returnType = m.getReturnType();
                    for (String typeName : typeNames) {
                        try {
                            if (tccl.loadClass(typeName).isAssignableFrom(returnType)) {
                                return true;
                            }
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                    if (paramCount >= 0) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf(e, "Failed to inspect return type of %s.%s", clazz.getName(), methodName);
        }
        return false;
    }

    private static Map<String, String> resolvedScopeToPath(JsonRPCMethodsBuildItem methods,
            HttpRootPathBuildItem httpRootPath) {
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, String> entry : methods.getScopeToPath().entrySet()) {
            resolved.put(entry.getKey(), httpRootPath.resolvePath(entry.getValue()));
        }
        return resolved;
    }
}
