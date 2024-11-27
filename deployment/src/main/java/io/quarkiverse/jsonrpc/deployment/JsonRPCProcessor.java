package io.quarkiverse.jsonrpc.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import io.quarkiverse.jsonrpc.deployment.config.JsonRPCConfig;
import io.quarkiverse.jsonrpc.runtime.JsonRPCRecorder;
import io.quarkiverse.jsonrpc.runtime.JsonRPCRouter;
import io.quarkiverse.jsonrpc.runtime.JsonRPCWebSocket;
import io.quarkiverse.jsonrpc.runtime.Keys;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Multi;

public class JsonRPCProcessor {
    private static final DotName JSON_RPC_API = DotName.createSimple("io.quarkiverse.jsonrpc.api.JsonRPCApi");
    private static final String FEATURE = "json-rpc";
    private static final String CONSTRUCTOR = "<init>";

    private final ClassLoader tccl = Thread.currentThread().getContextClassLoader();

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
            CombinedIndexBuildItem combinedIndexBuildItem,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {

        IndexView index = combinedIndexBuildItem.getIndex();

        Collection<AnnotationInstance> jsonRPCApiAnnotatoins = index.getAnnotations(JSON_RPC_API);

        Map<JsonRPCMethodName, JsonRPCMethod> methodsMap = new HashMap<>(); // All methods so that we can build the reflection

        List<String> requestResponseMethods = new ArrayList<>(); // All requestResponse methods for validation on the client side
        List<String> subscriptionMethods = new ArrayList<>(); // All subscription methods for validation on the client side

        Set<String> nativeClasses = new HashSet<>(); // Gather all classes that should be registered for native

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

                            String fullName = null;

                            // Also create the map to pass to the runtime for the relection calls

                            if (method.parametersCount() > 0) {
                                Map<String, Class> params = new LinkedHashMap<>(); // Keep the order
                                for (int i = 0; i < method.parametersCount(); i++) {
                                    Type parameterType = method.parameterType(i);
                                    Class parameterClass = toClass(parameterType);
                                    String parameterName = method.parameterName(i);
                                    params.put(parameterName, parameterClass);
                                }
                                fullName = Keys.createKey(scope, method.name(), params.keySet());

                                JsonRPCMethodName jsonRpcMethodName = new JsonRPCMethodName(fullName,
                                        Keys.createOrderedParameterKey(scope, method.name(), method.parametersCount()));
                                JsonRPCMethod jsonRpcMethod = new JsonRPCMethod(clazz, method.name(), params);
                                jsonRpcMethod.setExplicitlyBlocking(method.hasAnnotation(Blocking.class));
                                jsonRpcMethod
                                        .setExplicitlyNonBlocking(method.hasAnnotation(NonBlocking.class));
                                methodsMap.put(jsonRpcMethodName, jsonRpcMethod);

                                nativeClasses.addAll(List.copyOf(params.keySet()));
                            } else {
                                fullName = Keys.createKey(scope, method.name());
                                JsonRPCMethodName jsonRpcMethodName = new JsonRPCMethodName(fullName, null);
                                JsonRPCMethod jsonRpcMethod = new JsonRPCMethod(clazz, method.name(), null);
                                jsonRpcMethod.setExplicitlyBlocking(method.hasAnnotation(Blocking.class));
                                jsonRpcMethod
                                        .setExplicitlyNonBlocking(method.hasAnnotation(NonBlocking.class));
                                methodsMap.put(jsonRpcMethodName, jsonRpcMethod);
                            }

                            // Add the return type
                            String returnType = getEffectiveReturnType(method.returnType());
                            if (returnType != null) {
                                nativeClasses.add(returnType);
                            }

                            // Create list of available methods for the Javascript side.
                            if (method.returnType().name().equals(DotName.createSimple(Multi.class.getName()))) {
                                subscriptionMethods.add(fullName);
                            } else {
                                requestResponseMethods.add(fullName);
                            }
                        }
                    }
                }
            }

            // TODO
            //            if (!jsonRpcMethods.isEmpty()) {
            //                extensionMethodsMap.put(extension, jsonRpcMethods);
            //            }
        }

        //        if (!methodsMap.isEmpty()) {
        jsonRPCMethodsProvider.produce(new JsonRPCMethodsBuildItem(methodsMap));
        //        }

        // TODO: This needs to be send via connection init

        //BuildTimeConstBuildItem methodInfo = new BuildTimeConstBuildItem("devui-jsonrpc");

        //if (!subscriptionMethods.isEmpty()) {
        //    methodInfo.addBuildTimeData("jsonRPCSubscriptions", subscriptionMethods);
        //}
        //if (!requestResponseMethods.isEmpty()) {
        //    methodInfo.addBuildTimeData("jsonRPCMethods", requestResponseMethods);
        //}

        // Add know other classes to native
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCResponse.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCRequest.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod.class.getName());
        nativeClasses.add(io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName.class.getName());

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
        if (jsonRPCConfig.webSocket.enabled) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(JsonRPCRouter.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(ObjectMapper.class))
                    .createWith(recorder.createJsonRpcRouter(jsonRPCMethodsBuildItem.getMethodsMap()))
                    .scope(ApplicationScoped.class)
                    .done());

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(JsonRPCWebSocket.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(JsonRPCRouter.class))
                    .createWith(recorder.createJsonRpcWebSocket())
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
        if (jsonRPCConfig.webSocket.enabled) {
            // Websocket for JsonRPC comms
            routeProducer.produce(
                    httpRootPathBuildItem.routeBuilder()
                            .route(jsonRPCConfig.webSocket.path)
                            .routeConfigKey("quarkus.json-rpc.web-socket.path")
                            .handler(recorder.webSocketHandler(beanContainerBuildItem.getValue()))
                            .build());
        }
    }

    private String getEffectiveReturnType(Type type) {
        // Add the return type
        switch (type.kind()) {
            case CLASS:
                return type.asClassType().name().toString();
            case ARRAY:
                return getEffectiveReturnType(type.asArrayType().componentType());
            case PARAMETERIZED_TYPE:
                return getEffectiveReturnType(type.asParameterizedType().arguments().get(0));
            default:
                break;
        }
        return null;
    }

    private Class toClass(Type type) {
        if (type.kind().equals(Type.Kind.PRIMITIVE)) {
            return JandexReflection.loadRawType(type);
        } else if (type.kind().equals(Type.Kind.VOID)) {
            // TODO: Handle this.
            throw new RuntimeException("Void method return detected, JsonRPC Method needs to return something.");
        } else {
            try {
                return tccl.loadClass(type.name().toString());
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
