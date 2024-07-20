package io.quarkiverse.jsonrpc.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.quarkiverse.jsonrpc.runtime.model.JsonRPCRequest;

/**
 * Helps with creating keys used to identify the method
 *
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
public class Keys {

    public static String createKey(JsonRPCRequest jsonRpcRequest) {
        String fullMethod = jsonRpcRequest.getMethod();
        if (!jsonRpcRequest.hasNamedParams()) {
            return fullMethod;
        }
        // Parameters
        String paramsString = formatKeys(jsonRpcRequest.getNamedParamKeys());
        return fullMethod + paramsString;
    }

    /**
     * Name is a combination of ClassName/scope and method name
     *
     * @return the key
     */
    public static String createKey(String scope, String method) {
        return createKey(scope, method, null);
    }

    /**
     * Name is a combination of ClassName/scope and method name and optionally ordered parameter names
     *
     * @return the key
     */
    public static String createKey(String scope, String method, Set<String> params) {
        String paramsString = formatKeys(params);
        return String.format(FULL_NAME_FORMAT, scope, method, paramsString);
    }

    /**
     * Key used to lookup the name based on unnamed parameters
     *
     * @return the key
     */
    public static String createOrderedParameterKey(String scope, String method, int numOfParameters) {
        return String.format(ORDERED_PARAM_FORMAT, scope, method, numOfParameters);
    }

    static String createOrderedParameterKey(JsonRPCRequest jsonRpcRequest) {
        String fullMethod = jsonRpcRequest.getMethod();
        if (!jsonRpcRequest.hasPositionedParams()) {
            return fullMethod;
        }
        // Parameters
        return fullMethod + "[" + jsonRpcRequest.getPositionedParams().length + "]";
    }

    private static String formatKeys(Set<String> params) {
        String paramsString = "";
        if (params != null && !params.isEmpty()) {
            // Sort the params
            List<String> paramList = new ArrayList<>(params);
            Collections.sort(paramList);
            paramsString = "(" + String.join("|", paramList) + ")";
        }
        return paramsString;
    }

    private static final String FULL_NAME_FORMAT = "%s#%s%s";
    private static final String ORDERED_PARAM_FORMAT = "%s#%s[%d]";

}
