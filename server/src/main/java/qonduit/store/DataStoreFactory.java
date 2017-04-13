package qonduit.store;

import qonduit.Configuration;
import qonduit.api.response.QonduitException;

public class DataStoreFactory {

    public static DataStore create(Configuration conf) throws QonduitException {

        return new DataStore(conf);
    }
}
