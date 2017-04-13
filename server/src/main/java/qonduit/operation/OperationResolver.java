package qonduit.operation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperationResolver {

    private static Logger LOG = LoggerFactory.getLogger(OperationResolver.class);
    private static Map<String, Operation> operations = null;

    static {
        Map<String, Operation> m = new HashMap<>();
        ServiceLoader.load(Operation.class).forEach(
                s -> {
                    LOG.info("Registering operation {} with object {}", s.getRequestType().getOperation(), s.getClass()
                            .getSimpleName());
                    m.put(s.getRequestType().getOperation(), s);
                });
        operations = Collections.unmodifiableMap(m);
        LOG.info("Registered operations: {}", operations);
    }

    public static Map<String, Operation> getOperations() {
        return operations;
    }

    public static Operation getOperation(String operation) throws InstantiationException, IllegalAccessException {
        Operation o = operations.get(operation);
        if (null == o) {
            return null;
        } else {
            return o.getClass().newInstance();
        }
    }

}
