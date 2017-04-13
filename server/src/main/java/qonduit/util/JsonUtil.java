package qonduit.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.operation.OperationResolver;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.SubtypeResolver;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public class JsonUtil {

    private static final Logger LOG = LoggerFactory.getLogger(JsonUtil.class);
    private static final ObjectMapper mapper = new ObjectMapper(new CBORFactory());
    // private static final ObjectMapper cbor = new ObjectMapper(new
    // CBORFactory());

    static {
        mapper.registerModule(new Jdk8Module());
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);

        SubtypeResolver resolver = mapper.getSubtypeResolver();
        OperationResolver.getOperations().forEach((k, v) -> {
            LOG.trace("Registering operation \"{}\" request class: {}", k, v.getRequestType().getClass());
            resolver.registerSubtypes(new NamedType(v.getRequestType().getClass(), k));
        });

    }

    public static ObjectMapper getObjectMapper() {
        return mapper;
    }

}
