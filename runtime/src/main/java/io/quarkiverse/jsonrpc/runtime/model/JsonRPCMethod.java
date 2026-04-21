package io.quarkiverse.jsonrpc.runtime.model;

import java.util.Map;

public final class JsonRPCMethod {
    private Class clazz;
    private String methodName;
    private Map<String, Class> params;

    private ExecutionMode executionMode = ExecutionMode.DEFAULT;

    public JsonRPCMethod() {
    }

    public JsonRPCMethod(Class clazz, String methodName, Map<String, Class> params) {
        this.clazz = clazz;
        this.methodName = methodName;
        this.params = params;
    }

    public Class getClazz() {
        return clazz;
    }

    public String getMethodName() {
        return methodName;
    }

    public Map<String, Class> getParams() {
        return params;
    }

    public boolean hasParams() {
        return this.params != null && !this.params.isEmpty();
    }

    public void setClazz(Class clazz) {
        this.clazz = clazz;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setParams(Map<String, Class> params) {
        this.params = params;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    @Override
    public String toString() {
        return clazz.getName() + ":" + methodName + "(" + params + ")";
    }
}
