package qonduit.store;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;

import qonduit.Configuration;
import qonduit.api.response.QonduitException;

public class DataStore {

    private final AccumuloClient client;

    public DataStore(Configuration conf) throws QonduitException {

        Configuration.Accumulo accumuloConf = conf.getAccumulo();
        client = Accumulo.newClient().to(accumuloConf.getInstanceName(), accumuloConf.getZookeepers())
                .as(accumuloConf.getUsername(), new PasswordToken(accumuloConf.getPassword())).build();
    }

    public AccumuloClient getConnector() {
        return client;
    }

}
