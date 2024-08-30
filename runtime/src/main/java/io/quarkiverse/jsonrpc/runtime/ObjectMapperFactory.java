package io.quarkiverse.jsonrpc.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectMapperFactory {

    private static ObjectMapper jsonObjectMapper = setupMapper(new ObjectMapper());

    private ObjectMapperFactory() {
    }

    private static ObjectMapper setupMapper(ObjectMapper mapper) {
        return mapper.findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public static ObjectMapper json() {
        return jsonObjectMapper;
    }
}