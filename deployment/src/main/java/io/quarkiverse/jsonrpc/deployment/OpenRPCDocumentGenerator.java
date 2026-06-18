package io.quarkiverse.jsonrpc.deployment;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethod;
import io.quarkiverse.jsonrpc.runtime.model.JsonRPCMethodName;

public class OpenRPCDocumentGenerator {

    private static final String OPENRPC_VERSION = "1.3.2";

    private static final Set<DotName> STREAMING_TYPES = Set.of(
            DotName.createSimple("io.smallrye.mutiny.Multi"),
            DotName.createSimple("java.util.concurrent.Flow.Publisher"));

    private static final Set<DotName> REACTIVE_WRAPPERS = Set.of(
            DotName.createSimple("io.smallrye.mutiny.Uni"),
            DotName.createSimple("io.smallrye.mutiny.Multi"),
            DotName.createSimple("java.util.concurrent.CompletionStage"),
            DotName.createSimple("java.util.concurrent.CompletableFuture"),
            DotName.createSimple("java.util.concurrent.Flow.Publisher"));

    private static final Set<DotName> COLLECTION_TYPES = Set.of(
            DotName.createSimple("java.util.List"),
            DotName.createSimple("java.util.Set"),
            DotName.createSimple("java.util.Collection"),
            DotName.createSimple("java.lang.Iterable"));

    private static final DotName MAP = DotName.createSimple("java.util.Map");
    private static final DotName OPTIONAL = DotName.createSimple("java.util.Optional");

    private static final DotName JAVA_LANG_OBJECT = DotName.createSimple("java.lang.Object");

    private static final DotName JSON_IGNORE = DotName.createSimple("com.fasterxml.jackson.annotation.JsonIgnore");
    private static final DotName JSON_PROPERTY = DotName.createSimple("com.fasterxml.jackson.annotation.JsonProperty");

    private final IndexView index;
    private final ObjectMapper mapper;
    private final boolean schemaSimpleNames;
    private final String title;
    private final String version;
    private final Map<String, ObjectNode> componentSchemas = new TreeMap<>();
    private final Set<DotName> schemasInProgress = new HashSet<>();

    public OpenRPCDocumentGenerator(IndexView index, boolean schemaSimpleNames, String title, String version) {
        this.index = index;
        this.schemaSimpleNames = schemaSimpleNames;
        this.title = title;
        this.version = version;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String generate(Map<JsonRPCMethodName, JsonRPCMethod> methodsMap) {
        ObjectNode root = mapper.createObjectNode();
        root.put("openrpc", OPENRPC_VERSION);

        ObjectNode info = mapper.createObjectNode();
        info.put("title", title);
        info.put("version", version);
        root.set("info", info);

        ArrayNode methods = mapper.createArrayNode();

        methodsMap.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getName()))
                .forEach(entry -> {
                    ObjectNode methodNode = buildMethodObject(entry.getKey(), entry.getValue());
                    if (methodNode != null) {
                        methods.add(methodNode);
                    }
                });
        root.set("methods", methods);

        if (!componentSchemas.isEmpty()) {
            ObjectNode components = mapper.createObjectNode();
            ObjectNode schemas = mapper.createObjectNode();
            componentSchemas.forEach(schemas::set);
            components.set("schemas", schemas);
            root.set("components", components);
        }

        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OpenRPC document", e);
        }
    }

    private ObjectNode buildMethodObject(JsonRPCMethodName methodName, JsonRPCMethod method) {
        MethodInfo jandexMethod = findJandexMethod(method);
        if (jandexMethod == null) {
            return null;
        }

        ObjectNode methodNode = mapper.createObjectNode();
        methodNode.put("name", methodName.getName());

        ArrayNode params = buildParams(method, jandexMethod);
        methodNode.set("params", params);

        Type returnType = jandexMethod.returnType();
        boolean streaming = isStreamingType(returnType);
        Type effectiveReturnType = unwrapReactiveType(returnType);

        if (effectiveReturnType.kind() != Type.Kind.VOID) {
            ObjectNode result = mapper.createObjectNode();
            result.put("name", "result");
            result.set("schema", typeToJsonSchema(effectiveReturnType));
            methodNode.set("result", result);
        }

        if (streaming) {
            ArrayNode tags = mapper.createArrayNode();
            ObjectNode tag = mapper.createObjectNode();
            tag.put("name", "subscription");
            tags.add(tag);
            methodNode.set("tags", tags);
        }

        return methodNode;
    }

    private ArrayNode buildParams(JsonRPCMethod method, MethodInfo jandexMethod) {
        ArrayNode params = mapper.createArrayNode();
        if (!method.hasParams()) {
            return params;
        }

        List<String> paramNames = List.copyOf(method.getParams().keySet());
        for (int i = 0; i < paramNames.size(); i++) {
            Type paramType = jandexMethod.parameterType(i);
            ObjectNode param = mapper.createObjectNode();
            param.put("name", paramNames.get(i));
            param.put("required", !isOptionalType(paramType));
            param.set("schema", typeToJsonSchema(paramType));
            params.add(param);
        }
        return params;
    }

    private boolean isOptionalType(Type type) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            return OPTIONAL.equals(type.asParameterizedType().name());
        }
        if (type.kind() == Type.Kind.CLASS) {
            return OPTIONAL.equals(type.asClassType().name());
        }
        return false;
    }

    private ObjectNode typeToJsonSchema(Type type) {
        switch (type.kind()) {
            case VOID:
                return mapper.createObjectNode();
            case PRIMITIVE:
                return primitiveSchema(type.asPrimitiveType().primitive().name());
            case ARRAY:
                return arraySchema(type.asArrayType().componentType());
            case CLASS:
                return classTypeSchema(type.asClassType().name());
            case PARAMETERIZED_TYPE:
                return parameterizedTypeSchema(type.asParameterizedType());
            default:
                return mapper.createObjectNode();
        }
    }

    private ObjectNode primitiveSchema(String primitiveName) {
        ObjectNode schema = mapper.createObjectNode();
        switch (primitiveName) {
            case "BOOLEAN":
                schema.put("type", "boolean");
                break;
            case "BYTE":
            case "SHORT":
            case "INT":
                schema.put("type", "integer");
                break;
            case "LONG":
                schema.put("type", "integer");
                schema.put("format", "int64");
                break;
            case "FLOAT":
                schema.put("type", "number");
                schema.put("format", "float");
                break;
            case "DOUBLE":
                schema.put("type", "number");
                schema.put("format", "double");
                break;
            case "CHAR":
                schema.put("type", "string");
                break;
            default:
                break;
        }
        return schema;
    }

    private ObjectNode arraySchema(Type componentType) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "array");
        schema.set("items", typeToJsonSchema(componentType));
        return schema;
    }

    private ObjectNode classTypeSchema(DotName name) {
        String fqn = name.toString();

        // Boxed primitives
        switch (fqn) {
            case "java.lang.String":
            case "java.lang.Character":
                return schemaWith("string", null);
            case "java.lang.Boolean":
                return schemaWith("boolean", null);
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
                return schemaWith("integer", null);
            case "java.lang.Long":
                return schemaWith("integer", "int64");
            case "java.lang.Float":
                return schemaWith("number", "float");
            case "java.lang.Double":
                return schemaWith("number", "double");
            case "java.lang.Number":
                return schemaWith("number", null);

            // Well-known types
            case "java.util.UUID":
                return schemaWith("string", "uuid");
            case "java.time.LocalDateTime":
            case "java.time.OffsetDateTime":
            case "java.time.ZonedDateTime":
            case "java.time.Instant":
                return schemaWith("string", "date-time");
            case "java.time.LocalDate":
                return schemaWith("string", "date");
            case "java.time.LocalTime":
            case "java.time.OffsetTime":
                return schemaWith("string", "time");
            case "java.time.Duration":
            case "java.time.Period":
                return schemaWith("string", null);
            case "java.math.BigDecimal":
                return schemaWith("number", null);
            case "java.math.BigInteger":
                return schemaWith("integer", null);
            case "java.lang.Object":
            case "com.fasterxml.jackson.databind.JsonNode":
                return mapper.createObjectNode();
        }

        // Enum check via Jandex
        ClassInfo classInfo = index.getClassByName(name);
        if (classInfo != null && classInfo.isEnum()) {
            return enumSchema(classInfo);
        }

        // POJO — generate component schema and return $ref
        return generatePojoRef(name);
    }

    private ObjectNode parameterizedTypeSchema(ParameterizedType type) {
        DotName rawName = type.name();
        List<Type> args = type.arguments();

        // Optional<T> → schema for T
        if (OPTIONAL.equals(rawName) && !args.isEmpty()) {
            return typeToJsonSchema(args.get(0));
        }

        // Reactive wrappers — unwrap
        if (REACTIVE_WRAPPERS.contains(rawName) && !args.isEmpty()) {
            return typeToJsonSchema(args.get(0));
        }

        // Collection types → array
        if (COLLECTION_TYPES.contains(rawName)) {
            ObjectNode schema = mapper.createObjectNode();
            schema.put("type", "array");
            schema.set("items", args.isEmpty() ? mapper.createObjectNode() : typeToJsonSchema(args.get(0)));
            return schema;
        }

        // Map<K, V> → object with additionalProperties
        if (MAP.equals(rawName)) {
            ObjectNode schema = mapper.createObjectNode();
            schema.put("type", "object");
            schema.set("additionalProperties", args.size() >= 2 ? typeToJsonSchema(args.get(1)) : mapper.createObjectNode());
            return schema;
        }

        // Fall back to raw type
        return classTypeSchema(rawName);
    }

    private ObjectNode enumSchema(ClassInfo classInfo) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "string");
        ArrayNode enumValues = mapper.createArrayNode();
        for (FieldInfo field : classInfo.fields()) {
            if (field.type().name().equals(classInfo.name())) {
                enumValues.add(field.name());
            }
        }
        schema.set("enum", enumValues);
        return schema;
    }

    private ObjectNode generatePojoRef(DotName name) {
        String schemaKey = schemaSimpleNames ? name.local() : name.toString();

        if (!componentSchemas.containsKey(schemaKey) && !schemasInProgress.contains(name)) {
            schemasInProgress.add(name);
            ObjectNode pojoSchema = buildPojoSchema(name);
            schemasInProgress.remove(name);
            if (pojoSchema == null) {
                return mapper.createObjectNode();
            }
            componentSchemas.put(schemaKey, pojoSchema);
        }

        return createRef(schemaKey);
    }

    private ObjectNode buildPojoSchema(DotName name) {
        ClassInfo classInfo = index.getClassByName(name);
        if (classInfo == null) {
            return null;
        }

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();

        ClassInfo current = classInfo;
        while (current != null) {
            collectProperties(current, properties);
            DotName superName = current.superName();
            if (superName == null || JAVA_LANG_OBJECT.equals(superName)) {
                break;
            }
            current = index.getClassByName(superName);
        }

        if (!properties.isEmpty()) {
            schema.set("properties", properties);
        }
        return schema;
    }

    private void collectProperties(ClassInfo classInfo, ObjectNode properties) {
        Set<String> ignoredByField = new HashSet<>();
        Set<String> ignoredByGetter = new HashSet<>();
        Map<String, String> fieldRenames = new HashMap<>();

        for (FieldInfo field : classInfo.fields()) {
            if (Modifier.isStatic(field.flags())) {
                continue;
            }
            if (field.hasAnnotation(JSON_IGNORE)) {
                ignoredByField.add(field.name());
                continue;
            }
            String resolvedName = resolvePropertyName(field.annotation(JSON_PROPERTY), field.name());
            if (!resolvedName.equals(field.name())) {
                fieldRenames.put(field.name(), resolvedName);
            }
        }

        for (MethodInfo method : classInfo.methods()) {
            String methodName = method.name();
            if (method.parametersCount() != 0 || method.returnType().kind() == Type.Kind.VOID) {
                continue;
            }
            String derivedName = derivePropertyName(methodName);
            if (derivedName == null) {
                continue;
            }
            if (method.hasAnnotation(JSON_IGNORE)) {
                ignoredByGetter.add(derivedName);
                continue;
            }
            if (!ignoredByField.contains(derivedName)) {
                String propertyName = resolvePropertyName(method.annotation(JSON_PROPERTY), derivedName);
                if (propertyName.equals(derivedName) && fieldRenames.containsKey(derivedName)) {
                    propertyName = fieldRenames.get(derivedName);
                }
                if (!properties.has(propertyName)) {
                    properties.set(propertyName, typeToJsonSchema(method.returnType()));
                }
            }
        }

        Set<String> allIgnored = new HashSet<>(ignoredByField);
        allIgnored.addAll(ignoredByGetter);

        for (FieldInfo field : classInfo.fields()) {
            if (Modifier.isStatic(field.flags()) || !Modifier.isPublic(field.flags())) {
                continue;
            }
            if (allIgnored.contains(field.name())) {
                continue;
            }
            String resolvedName = resolvePropertyName(field.annotation(JSON_PROPERTY), field.name());
            if (!properties.has(resolvedName)) {
                properties.set(resolvedName, typeToJsonSchema(field.type()));
            }
        }
    }

    private String derivePropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return null;
    }

    private String resolvePropertyName(AnnotationInstance jsonProperty, String defaultName) {
        if (jsonProperty != null) {
            AnnotationValue value = jsonProperty.value();
            if (value != null && !value.asString().isEmpty()) {
                return value.asString();
            }
        }
        return defaultName;
    }

    private ObjectNode createRef(String typeName) {
        ObjectNode ref = mapper.createObjectNode();
        ref.put("$ref", "#/components/schemas/" + typeName);
        return ref;
    }

    private ObjectNode schemaWith(String type, String format) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", type);
        if (format != null) {
            schema.put("format", format);
        }
        return schema;
    }

    private Type unwrapReactiveType(Type type) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            DotName rawName = type.asParameterizedType().name();
            if (REACTIVE_WRAPPERS.contains(rawName)) {
                List<Type> args = type.asParameterizedType().arguments();
                if (!args.isEmpty()) {
                    return args.get(0);
                }
            }
        }
        return type;
    }

    private boolean isStreamingType(Type type) {
        DotName name;
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            name = type.asParameterizedType().name();
        } else if (type.kind() == Type.Kind.CLASS) {
            name = type.asClassType().name();
        } else {
            return false;
        }
        return STREAMING_TYPES.contains(name);
    }

    @SuppressWarnings("rawtypes")
    private MethodInfo findJandexMethod(JsonRPCMethod method) {
        ClassInfo classInfo = index.getClassByName(method.getClazz().getName());
        if (classInfo == null) {
            return null;
        }
        List<Class> paramTypes = method.hasParams() ? List.copyOf(method.getParams().values()) : List.of();
        for (MethodInfo mi : classInfo.methods()) {
            if (!mi.name().equals(method.getMethodName()) || mi.parametersCount() != paramTypes.size()) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < paramTypes.size(); i++) {
                if (!mi.parameterType(i).name().toString().equals(paramTypes.get(i).getName())) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return mi;
            }
        }
        return null;
    }
}
