package io.quarkiverse.jsonrpc.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.JandexReflection;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jsonrpc.api.JsonRPCBroadcaster;
import io.quarkiverse.jsonrpc.deployment.config.JsonRPCConfig;
import io.quarkiverse.jsonrpc.runtime.JsonRPCRecorder;
import io.quarkiverse.jsonrpc.runtime.JsonRPCRouter;
import io.quarkiverse.jsonrpc.runtime.JsonRPCSessions;
import io.quarkiverse.jsonrpc.runtime.Keys;
import io.quarkiverse.jsonrpc.runtime.devui.JsonRPCDevUIService;
import io.quarkiverse.jsonrpc.runtime.model.ExecutionMode;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCCodec;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.buildtime.BuildTimeActionBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.vertx.http.deployment.FilterBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;

public class JsonRPCProcessor {
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(JsonRPCProcessor.class);
    private static final DotName JSON_RPC_API = DotName.createSimple("io.quarkiverse.jsonrpc.api.JsonRPCApi");
    private static final Pattern JS_IDENTIFIER = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*$");
    private static final Set<String> JS_RESERVED_WORDS = Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "new", "null", "return", "super",
            "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with",
            "yield", "let", "static", "implements", "interface", "package", "private", "protected",
            "public", "await");
    private static final String FEATURE = "json-rpc";
    private static final String CONSTRUCTOR = "<init>";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void additionalBeanDefiningAnnotation(BuildProducer<BeanDefiningAnnotationBuildItem> beanDefiningAnnotationProducer) {
        // Make ArC discover the beans marked with the @JsonRPCApi qualifier
        beanDefiningAnnotationProducer
                .produce(new BeanDefiningAnnotationBuildItem(JSON_RPC_API, BuiltinScope.SINGLETON.getName()));
    }

    @BuildStep
    void findAllJsonRPCMethods(BuildProducer<JsonRPCMethodsBuildItem> jsonRPCMethodsProvider,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            CombinedIndexBuildItem combinedIndexBuildItem) {

        IndexView index = combinedIndexBuildItem.getIndex();

        Collection<AnnotationInstance> jsonRPCApiAnnotatoins = index.getAnnotations(JSON_RPC_API);

        Map<JsonRPCMethodName, JsonRPCMethod> methodsMap = new HashMap<>();
        Set<String> nativeClasses = new HashSet<>();

        // Let's use the Jandex index to find all methods
        for (AnnotationInstance annotationInstance : jsonRPCApiAnnotatoins) {
            AnnotationTarget target = annotationInstance.target();
            ClassInfo classInfo = target.asClass();
            AnnotationValue annotationValue = annotationInstance.value();
            String scope = classInfo.simpleName();
            if (annotationValue != null && !annotationValue.asString().equals("_DEFAULT_SCOPE_")) {
                scope = annotationValue.asString();
            }

            Class clazz = JandexReflection.loadClass(classInfo);

            List<MethodInfo> methods = classInfo.methods();

            for (MethodInfo method : methods) {
                if (!method.name().equals(CONSTRUCTOR)) { // Ignore constructor
                    if (Modifier.isPublic(method.flags())) { // Only allow public methods
                        if (method.returnType().kind() != Type.Kind.VOID) { // TODO: Only allow method with response ? Maybe not

                            if (method.hasAnnotation(Blocking.class) && method.hasAnnotation(NonBlocking.class)) {
                                throw new IllegalArgumentException(
                                        "Method " + classInfo.name() + "." + method.name()
                                                + " cannot be annotated with both @Blocking and @NonBlocking");
                            }
                            if (method.hasAnnotation(RunOnVirtualThread.class)
                                    && method.hasAnnotation(NonBlocking.class)) {
                                throw new IllegalArgumentException(
                                        "Method " + classInfo.name() + "." + method.name()
                                                + " cannot be annotated with both @RunOnVirtualThread and @NonBlocking");
                            }
                            if (method.hasAnnotation(RunOnVirtualThread.class)) {
                                String returnTypeName = method.returnType().name().toString();
                                if (returnTypeName.equals("io.smallrye.mutiny.Multi")
                                        || returnTypeName.equals("java.util.concurrent.Flow$Publisher")) {
                                    throw new IllegalArgumentException(
                                            "Method " + classInfo.name() + "." + method.name()
                                                    + " cannot use @RunOnVirtualThread with a streaming return type"
                                                    + " (Multi/Flow.Publisher)");
                                }
                            }
                            if (method.hasAnnotation(RunOnVirtualThread.class)
                                    && method.hasAnnotation(Blocking.class)) {
                                LOG.warnf("Method %s.%s is annotated with both @RunOnVirtualThread and @Blocking."
                                        + " @Blocking is redundant and will be ignored.",
                                        classInfo.name(), method.name());
                            }

                            ExecutionMode executionMode;
                            if (method.hasAnnotation(RunOnVirtualThread.class)) {
                                executionMode = ExecutionMode.VIRTUAL_THREAD;
                            } else if (method.hasAnnotation(Blocking.class)) {
                                executionMode = ExecutionMode.BLOCKING;
                            } else if (method.hasAnnotation(NonBlocking.class)) {
                                executionMode = ExecutionMode.NON_BLOCKING;
                            } else {
                                executionMode = ExecutionMode.DEFAULT;
                            }

                            String fullName = null;

                            if (method.parametersCount() > 0) {
                                Map<String, Class> params = new LinkedHashMap<>(); // Keep the order
                                for (int i = 0; i < method.parametersCount(); i++) {
                                    Type parameterType = method.parameterType(i);
                                    Class parameterClass = toClass(parameterType);
                                    String parameterName = method.parameterName(i);
                                    params.put(parameterName, parameterClass);
                                    nativeClasses.addAll(getEffectiveTypes(parameterType));
                                }
                                fullName = Keys.createKey(scope, method.name(), params.keySet());

                                JsonRPCMethodName jsonRpcMethodName = new JsonRPCMethodName(fullName,
                                        Keys.createOrderedParameterKey(scope, method.name(), method.parametersCount()));
                                JsonRPCMethod jsonRpcMethod = new JsonRPCMethod(clazz, method.name(), params);
                                jsonRpcMethod.setExecutionMode(executionMode);
                                methodsMap.put(jsonRpcMethodName, jsonRpcMethod);
                            } else {
                                fullName = Keys.createKey(scope, method.name());
                                JsonRPCMethodName jsonRpcMethodName = new JsonRPCMethodName(fullName, null);
                                JsonRPCMethod jsonRpcMethod = new JsonRPCMethod(clazz, method.name(), null);
                                jsonRpcMethod.setExecutionMode(executionMode);
                                methodsMap.put(jsonRpcMethodName, jsonRpcMethod);
                            }

                            // Add the return type
                            nativeClasses.addAll(getEffectiveTypes(method.returnType()));

                        }
                    }
                }
            }

        }

        jsonRPCMethodsProvider.produce(new JsonRPCMethodsBuildItem(methodsMap));

        // Add known classes to native
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCResponse.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCRequest.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCNotification.class.getName());
        nativeClasses.add(JsonRPCSessions.class.getName());
        nativeClasses.add(JsonRPCBroadcaster.class.getName());

        // Make sure it's available in native
        reflectiveClassProducer
                .produce(ReflectiveClassBuildItem.builder(nativeClasses.toArray(new String[] {})).methods()
                        .fields().build());

    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createBeans(
            JsonRPCConfig jsonRPCConfig,
            JsonRPCRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem) {
        if (jsonRPCConfig.webSocket().enabled()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(JsonRPCSessions.class)
                    .setRuntimeInit()
                    .unremovable()
                    .supplier(recorder.createJsonRpcSessions())
                    .scope(ApplicationScoped.class)
                    .done());

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(JsonRPCCodec.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(ObjectMapper.class))
                    .createWith(recorder.createJsonRpcCodec())
                    .scope(ApplicationScoped.class)
                    .done());

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(JsonRPCRouter.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(JsonRPCCodec.class))
                    .addInjectionPoint(ClassType.create(JsonRPCSessions.class))
                    .createWith(recorder.createJsonRpcRouter(jsonRPCMethodsBuildItem.getMethodsMap()))
                    .scope(ApplicationScoped.class)
                    .done());

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(JsonRPCBroadcaster.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(JsonRPCCodec.class))
                    .addInjectionPoint(ClassType.create(JsonRPCSessions.class))
                    .createWith(recorder.createJsonRpcBroadcaster())
                    .scope(ApplicationScoped.class)
                    .done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerHandlers(
            JsonRPCConfig jsonRPCConfig,
            JsonRPCRecorder recorder,
            BuildProducer<RouteBuildItem> routeProducer,
            BeanContainerBuildItem beanContainerBuildItem,
            HttpRootPathBuildItem httpRootPathBuildItem) {
        if (jsonRPCConfig.webSocket().enabled()) {
            // Websocket for JsonRPC comms
            routeProducer.produce(
                    httpRootPathBuildItem.routeBuilder()
                            .route(jsonRPCConfig.webSocket().path())
                            .routeConfigKey("quarkus.json-rpc.web-socket.path")
                            .handler(recorder.webSocketHandler(beanContainerBuildItem.getValue()))
                            .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerSubProtocolFilter(
            JsonRPCConfig jsonRPCConfig,
            JsonRPCRecorder recorder,
            HttpRootPathBuildItem httpRootPathBuildItem,
            BuildProducer<FilterBuildItem> filterProducer) {
        if (jsonRPCConfig.webSocket().enabled()) {
            String resolvedPath = httpRootPathBuildItem.resolvePath(jsonRPCConfig.webSocket().path());
            filterProducer.produce(new FilterBuildItem(recorder.subProtocolHandler(resolvedPath), 300));
        }
    }

    // JavaScript client proxy

    @BuildStep
    void generateJsClient(
            JsonRPCConfig jsonRPCConfig,
            JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer) {

        if (!jsonRPCConfig.client().enabled()) {
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
                            is.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // 2. Generate the typed proxy module
        Map<JsonRPCMethodName, JsonRPCMethod> methodsMap = jsonRPCMethodsBuildItem.getMethodsMap();
        String proxyJs = generateTypedProxy(methodsMap, jsonRPCConfig.webSocket().path());
        staticResourceProducer.produce(
                new GeneratedStaticResourceBuildItem(
                        "/_static/quarkus-json-rpc-api/jsonrpc-api.js",
                        proxyJs.getBytes(StandardCharsets.UTF_8)));

        // 3. Generate importmap.json for web-dependency-locator
        String importmap = generateImportMap();
        generatedResourceProducer.produce(
                new GeneratedResourceBuildItem(
                        "META-INF/importmap.json",
                        importmap.getBytes(StandardCharsets.UTF_8)));
    }

    private String generateTypedProxy(Map<JsonRPCMethodName, JsonRPCMethod> methodsMap, String wsPath) {
        StringBuilder js = new StringBuilder();
        js.append("import { JsonRPCClient } from '/_static/quarkus-json-rpc/jsonrpc-client.js';\n\n");
        js.append("export const client = new JsonRPCClient({ path: '").append(escapeJsString(wsPath)).append("' });\n\n");

        // Group methods by scope and method name (sorted for deterministic output).
        // Multiple overloads of the same method name share one JS proxy entry — the server
        // resolves the correct overload based on parameters. We validate that all overloads
        // agree on streaming vs non-streaming return type.
        Map<String, Map<String, MethodEntry>> byScope = new java.util.TreeMap<>();
        for (Map.Entry<JsonRPCMethodName, JsonRPCMethod> entry : methodsMap.entrySet()) {
            String key = entry.getKey().getName();
            int hashIdx = key.indexOf('#');
            String scope = key.substring(0, hashIdx);
            validateJsIdentifier(scope, "@JsonRPCApi scope");
            String methodName = entry.getValue().getMethodName();
            validateJsIdentifier(methodName, "Method name '" + scope + "#" + methodName + "'");
            Map<String, MethodEntry> scopeMethods = byScope.computeIfAbsent(scope, k -> new java.util.TreeMap<>());
            MethodEntry existing = scopeMethods.get(methodName);
            if (existing != null) {
                // Overloaded method — verify return types agree on streaming vs non-streaming
                boolean existingStreaming = isStreamingReturnType(existing.method);
                boolean newStreaming = isStreamingReturnType(entry.getValue());
                if (existingStreaming != newStreaming) {
                    throw new IllegalArgumentException(
                            "Overloaded method '" + scope + "#" + methodName + "' has conflicting return types: "
                                    + "some overloads return a streaming type (Multi/Flow.Publisher) while others do not. "
                                    + "The JavaScript client proxy cannot represent both call() and subscribe() "
                                    + "under the same method name. Rename one of the overloads or make all overloads "
                                    + "return the same category (all streaming or all non-streaming).");
                }
            } else {
                scopeMethods.put(methodName, new MethodEntry(scope, methodName, entry.getValue()));
            }
        }

        for (Map.Entry<String, Map<String, MethodEntry>> scopeEntry : byScope.entrySet()) {
            String scope = scopeEntry.getKey();
            Map<String, MethodEntry> methods = scopeEntry.getValue();

            js.append("export const ").append(scope).append(" = {\n");
            int i = 0;
            for (MethodEntry me : methods.values()) {
                boolean streaming = isStreamingReturnType(me.method);
                String clientMethod = streaming ? "subscribe" : "call";
                String methodKey = me.scope + "#" + me.methodName;
                js.append("    ").append(me.methodName)
                        .append(": (params) => client.").append(clientMethod)
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

    private String generateImportMap() {
        return "{\n"
                + "  \"imports\" : {\n"
                + "    \"@quarkiverse/json-rpc\" : \"/_static/quarkus-json-rpc/jsonrpc-client.js\",\n"
                + "    \"@quarkiverse/json-rpc/\" : \"/_static/quarkus-json-rpc/\",\n"
                + "    \"@quarkiverse/json-rpc-api\" : \"/_static/quarkus-json-rpc-api/jsonrpc-api.js\"\n"
                + "  }\n"
                + "}\n";
    }

    private static final Set<String> STREAMING_TYPES = Set.of(
            "io.smallrye.mutiny.Multi",
            "java.util.concurrent.Flow$Publisher");

    private boolean isStreamingReturnType(JsonRPCMethod method) {
        int paramCount = method.hasParams() ? method.getParams().size() : 0;
        return isReturnTypeAssignableTo(method.getClazz(), method.getMethodName(), paramCount, STREAMING_TYPES);
    }

    private record MethodEntry(String scope, String methodName, JsonRPCMethod method) {
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

    // Dev UI

    private static final DotName ROLES_ALLOWED = DotName.createSimple("jakarta.annotation.security.RolesAllowed");
    private static final DotName PERMIT_ALL = DotName.createSimple("jakarta.annotation.security.PermitAll");
    private static final DotName DENY_ALL = DotName.createSimple("jakarta.annotation.security.DenyAll");
    private static final DotName AUTHENTICATED = DotName.createSimple("io.quarkus.security.Authenticated");

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem createDevUICard(JsonRPCConfig jsonRPCConfig, JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem,
            CombinedIndexBuildItem combinedIndexBuildItem) {
        CardPageBuildItem card = new CardPageBuildItem();
        IndexView index = combinedIndexBuildItem.getIndex();

        // Build-time data: registered methods table
        List<Map<String, Object>> methodsList = new ArrayList<>();
        for (Map.Entry<JsonRPCMethodName, JsonRPCMethod> entry : jsonRPCMethodsBuildItem.getMethodsMap().entrySet()) {
            Map<String, Object> methodData = new LinkedHashMap<>();
            JsonRPCMethodName methodName = entry.getKey();
            JsonRPCMethod method = entry.getValue();

            methodData.put("key", methodName.getName());
            methodData.put("className", method.getClazz().getSimpleName());
            methodData.put("methodName", method.getMethodName());

            if (method.hasParams()) {
                List<String> params = new ArrayList<>();
                for (Map.Entry<String, Class> p : method.getParams().entrySet()) {
                    params.add(p.getKey() + ": " + p.getValue().getSimpleName());
                }
                methodData.put("parameters", String.join(", ", params));
            } else {
                methodData.put("parameters", "");
            }

            String execMode = "blocking (default)";
            if (method.getExecutionMode() == ExecutionMode.VIRTUAL_THREAD) {
                execMode = "virtual thread";
            } else if (method.getExecutionMode() == ExecutionMode.BLOCKING) {
                execMode = "blocking";
            } else if (method.getExecutionMode() == ExecutionMode.NON_BLOCKING) {
                execMode = "non-blocking";
            } else if (isReactiveReturnType(method.getClazz(), method.getMethodName())) {
                execMode = "non-blocking (default)";
            }
            methodData.put("executionMode", execMode);

            // Detect security annotations
            methodData.put("security", resolveSecurityConstraint(index, method));

            methodsList.add(methodData);
        }
        card.addBuildTimeData("methods", methodsList,
                "All registered JSON-RPC methods with their signatures, execution modes, and security constraints", true);
        card.addBuildTimeData("endpointPath", jsonRPCConfig.webSocket().path(),
                "The WebSocket endpoint path for JSON-RPC connections", true);

        // Methods table page
        card.addPage(Page.webComponentPageBuilder()
                .title("Methods")
                .icon("font-awesome-solid:list")
                .componentLink("qwc-json-rpc-methods.js"));

        // Interactive tester page
        card.addPage(Page.webComponentPageBuilder()
                .title("Tester")
                .icon("font-awesome-solid:play")
                .componentLink("qwc-json-rpc-tester.js"));

        // Active sessions page
        card.addPage(Page.webComponentPageBuilder()
                .title("Sessions")
                .icon("font-awesome-solid:plug")
                .componentLink("qwc-json-rpc-sessions.js"));

        return card;
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem createDevUIJsonRPCService() {
        return new JsonRPCProvidersBuildItem(JsonRPCDevUIService.class);
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    BuildTimeActionBuildItem createDevUIBuildTimeActions(JsonRPCConfig jsonRPCConfig) {
        BuildTimeActionBuildItem actions = new BuildTimeActionBuildItem();
        String path = jsonRPCConfig.webSocket().path();
        actions.actionBuilder()
                .methodName("getEndpointPath")
                .description("Get the configured WebSocket endpoint path for JSON-RPC connections")
                .function(params -> path)
                .enableMcpFuctionByDefault()
                .build();
        return actions;
    }

    /**
     * Resolve the effective security constraint for a JSON-RPC method by checking
     * for security annotations on the method first, then falling back to the class.
     *
     * @return a human-readable security label, or empty string if unsecured
     */
    private String resolveSecurityConstraint(IndexView index, JsonRPCMethod method) {
        ClassInfo classInfo = index.getClassByName(method.getClazz().getName());
        if (classInfo == null) {
            return "";
        }

        // Find the matching method in the Jandex index
        MethodInfo methodInfo = null;
        for (MethodInfo mi : classInfo.methods()) {
            if (mi.name().equals(method.getMethodName())) {
                int paramCount = method.hasParams() ? method.getParams().size() : 0;
                if (mi.parametersCount() == paramCount) {
                    methodInfo = mi;
                    break;
                }
            }
        }

        // Check method-level annotations first (they override class-level)
        if (methodInfo != null) {
            String methodSecurity = getSecurityLabel(methodInfo);
            if (methodSecurity != null) {
                return methodSecurity;
            }
        }

        // Fall back to class-level annotations
        String classSecurity = getSecurityLabel(classInfo);
        if (classSecurity != null) {
            return classSecurity;
        }

        return "";
    }

    private String getSecurityLabel(AnnotationTarget target) {
        if (target.hasAnnotation(ROLES_ALLOWED)) {
            AnnotationInstance ann = target.annotation(ROLES_ALLOWED);
            return "@RolesAllowed(" + formatRoles(ann) + ")";
        }
        if (target.hasAnnotation(PERMIT_ALL)) {
            return "@PermitAll";
        }
        if (target.hasAnnotation(DENY_ALL)) {
            return "@DenyAll";
        }
        if (target.hasAnnotation(AUTHENTICATED)) {
            return "@Authenticated";
        }
        return null;
    }

    private String formatRoles(AnnotationInstance rolesAllowed) {
        AnnotationValue value = rolesAllowed.value();
        if (value == null) {
            return "";
        }
        String[] roles = value.asStringArray();
        return String.join(", ", roles);
    }

    private static final Set<String> NON_STREAMING_REACTIVE_TYPES = Set.of(
            "io.smallrye.mutiny.Uni",
            "java.util.concurrent.CompletionStage");

    private static final Set<String> REACTIVE_TYPES;
    static {
        Set<String> all = new HashSet<>(NON_STREAMING_REACTIVE_TYPES);
        all.addAll(STREAMING_TYPES);
        REACTIVE_TYPES = Set.copyOf(all);
    }

    private boolean isReactiveReturnType(Class<?> clazz, String methodName) {
        return isReturnTypeAssignableTo(clazz, methodName, -1, REACTIVE_TYPES);
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

    private Set<String> getEffectiveTypes(Type type) {
        Set<String> types = new HashSet<>();
        switch (type.kind()) {
            case CLASS:
                types.add(type.asClassType().name().toString());
                break;
            case ARRAY:
                types.addAll(getEffectiveTypes(type.asArrayType().componentType()));
                break;
            case PARAMETERIZED_TYPE:
                for (Type arg : type.asParameterizedType().arguments()) {
                    types.addAll(getEffectiveTypes(arg));
                }
                break;
            default:
                break;
        }
        return types;
    }

    private Class toClass(Type type) {
        if (type.kind().equals(Type.Kind.VOID)) {
            throw new RuntimeException("Void method return detected, JsonRPC Method needs to return something.");
        }
        return JandexReflection.loadRawType(type);
    }

}
