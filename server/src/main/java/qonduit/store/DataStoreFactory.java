package qonduit.store;

import io.netty.handler.codec.http.HttpResponseStatus;
import qonduit.Configuration;
import qonduit.api.response.QonduitException;

public class DataStoreFactory {

    public static DataStore create(Configuration conf) throws QonduitException {
        try {
            return new DataStore(conf);
        } catch (RuntimeException e) {
            throw new QonduitException(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), "Error creating DataStoreImpl",
                    e.getMessage(), e);
        }

    }
}
