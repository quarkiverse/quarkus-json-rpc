package io.quarkiverse.jsonrpc.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import io.quarkiverse.jsonrpc.api.JsonRPCExceptionMapper;
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
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.FooterPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;

public class JsonRPCProcessor {
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(JsonRPCProcessor.class);
    private static final DotName JSON_RPC_API = DotName.createSimple("io.quarkiverse.jsonrpc.api.JsonRPCApi");
    private static final DotName JSON_RPC_IGNORE = DotName.createSimple("io.quarkiverse.jsonrpc.api.JsonRPCIgnore");
    private static final Pattern VALID_PATH_PATTERN = Pattern.compile("^/[a-zA-Z0-9._/-]+$");
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
    UnremovableBeanBuildItem keepExceptionMappers() {
        return UnremovableBeanBuildItem.beanTypes(JsonRPCExceptionMapper.class);
    }

    @BuildStep
    void findAllJsonRPCMethods(BuildProducer<JsonRPCMethodsBuildItem> jsonRPCMethodsProvider,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            CombinedIndexBuildItem combinedIndexBuildItem) {

        IndexView index = combinedIndexBuildItem.getIndex();

        Collection<AnnotationInstance> jsonRPCApiAnnotatoins = index.getAnnotations(JSON_RPC_API);

        Map<JsonRPCMethodName, JsonRPCMethod> methodsMap = new HashMap<>();
        Set<String> nativeClasses = new HashSet<>();
        Set<String> extraPaths = new LinkedHashSet<>();
        Map<String, String> scopeToPath = new HashMap<>();

        // Let's use the Jandex index to find all methods
        for (AnnotationInstance annotationInstance : jsonRPCApiAnnotatoins) {
            AnnotationTarget target = annotationInstance.target();
            ClassInfo classInfo = target.asClass();
            AnnotationValue annotationValue = annotationInstance.value();
            String scope = classInfo.simpleName();
            if (annotationValue != null && !annotationValue.asString().equals("_DEFAULT_SCOPE_")) {
                scope = annotationValue.asString();
            }

            nativeClasses.add(classInfo.name().toString());

            AnnotationValue pathValue = annotationInstance.value("path");
            if (pathValue != null && !pathValue.asString().isEmpty()) {
                String path = pathValue.asString();
                if (!path.startsWith("/")) {
                    throw new IllegalArgumentException(
                            "@JsonRPCApi path on " + classInfo.name() + " must start with '/', got: " + path);
                }
                // Normalize: strip trailing slashes
                while (path.length() > 1 && path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                if (!VALID_PATH_PATTERN.matcher(path).matches()) {
                    throw new IllegalArgumentException(
                            "@JsonRPCApi path on " + classInfo.name()
                                    + " contains invalid characters, got: " + path);
                }
                extraPaths.add(path);
                scopeToPath.put(scope, path);
            }

            Class clazz = JandexReflection.loadClass(classInfo);

            List<MethodInfo> methods = classInfo.methods();

            for (MethodInfo method : methods) {
                if (!method.name().equals(CONSTRUCTOR)) { // Ignore constructor
                    if (Modifier.isPublic(method.flags())) { // Only allow public methods
                        if (method.hasAnnotation(JSON_RPC_IGNORE)) {
                            continue;
                        }
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

                        if (method.returnType().kind() != Type.Kind.VOID) {
                            nativeClasses.addAll(getEffectiveTypes(method.returnType()));
                        }
                    }
                }
            }

        }

        jsonRPCMethodsProvider.produce(new JsonRPCMethodsBuildItem(methodsMap, extraPaths, scopeToPath));

        // Add known classes to native
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCResponse.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCRequest.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCNotification.class.getName());
        nativeClasses.add(JsonRPCSessions.class.getName());
        nativeClasses.add(JsonRPCBroadcaster.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.api.JsonRPCConnected.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.api.JsonRPCDisconnected.class.getName());

        // Make sure it's available in native
        reflectiveClassProducer
                .produce(ReflectiveClassBuildItem.builder(nativeClasses.toArray(new String[] {})).methods()
                        .fields().build());

    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createBeans(
            JsonRPCRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            JsonRPCMethodsBuildItem jsonRPCMethodsBuildItem) {
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
                .createWith(recorder.createJsonRpcRouter(
                        jsonRPCMethodsBuildItem.getMethodsMap()))
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

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerMetrics(
            JsonRPCRecorder recorder,
            Optional<MetricsCapabilityBuildItem> metricsCapability) {
        if (metricsCapability.isPresent()) {
            recorder.initMetrics();
        } else {
            recorder.clearMetrics();
        }
    }

    @BuildStep
    HealthBuildItem addHealthCheck(JsonRPCConfig config) {
        return new HealthBuildItem("io.quarkiverse.jsonrpc.runtime.JsonRPCHealthCheck",
                config.health().enabled());
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
        Map<String, String> scopeToPath = jsonRPCMethodsBuildItem.getScopeToPath();

        // Build-time data: registered methods table
        List<Map<String, Object>> methodsList = new ArrayList<>();
        for (Map.Entry<JsonRPCMethodName, JsonRPCMethod> entry : jsonRPCMethodsBuildItem.getMethodsMap().entrySet()) {
            Map<String, Object> methodData = new LinkedHashMap<>();
            JsonRPCMethodName methodName = entry.getKey();
            JsonRPCMethod method = entry.getValue();

            String key = methodName.getName();
            methodData.put("key", key);
            methodData.put("className", method.getClazz().getSimpleName());
            methodData.put("methodName", method.getMethodName());

            String scope = key.substring(0, key.indexOf('#'));
            String path = scopeToPath.getOrDefault(scope, "");
            methodData.put("path", path);

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
            }
            methodData.put("executionMode", execMode);

            // Detect security annotations
            methodData.put("security", resolveSecurityConstraint(index, method));

            methodsList.add(methodData);
        }
        card.addBuildTimeData("methods", methodsList,
                "All registered JSON-RPC methods with their signatures, execution modes, and security constraints", true);

        // Methods table page
        card.addPage(Page.webComponentPageBuilder()
                .title("Methods")
                .icon("font-awesome-solid:list")
                .componentLink("qwc-json-rpc-methods.js"));

        // Active sessions page
        card.addPage(Page.webComponentPageBuilder()
                .title("Sessions")
                .icon("font-awesome-solid:plug")
                .componentLink("qwc-json-rpc-sessions.js"));

        // OpenRPC schema viewers
        if (jsonRPCConfig.openrpc().enabled()) {
            card.addPage(Page.externalPageBuilder("OpenRPC")
                    .url(jsonRPCConfig.openrpc().path())
                    .isJsonContent()
                    .icon("font-awesome-solid:file-code"));

            for (String extraPath : jsonRPCMethodsBuildItem.getExtraPaths()) {
                card.addPage(Page.externalPageBuilder("OpenRPC (" + extraPath + ")")
                        .url(extraPath + "/openrpc.json")
                        .isJsonContent()
                        .icon("font-awesome-solid:file-code"));
            }
        }

        return card;
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem createDevUIJsonRPCService() {
        return new JsonRPCProvidersBuildItem(JsonRPCDevUIService.class);
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    FooterPageBuildItem createFooterLog() {
        FooterPageBuildItem footer = new FooterPageBuildItem();
        footer.addPage(Page.webComponentPageBuilder()
                .title("JSON-RPC")
                .icon("font-awesome-solid:exchange-alt")
                .componentLink("qwc-json-rpc-log.js"));
        return footer;
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
