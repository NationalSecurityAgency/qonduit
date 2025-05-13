package qonduit.store;

import java.io.IOException;

import qonduit.Configuration;

public class SplitStoreFactory {

    public static SplitStore create(Configuration conf, DataStore store) throws IOException {
        if (conf.getAccumulo().getSplitServer().isEnabled()) {
            SplitStore ss = new SplitStore(conf, store.getConnector());
            ss.initialize();
            return ss;
        } else {
            return null;
        }
    }

}
