package qonduit.store;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;

import io.netty.handler.codec.http.HttpResponseStatus;
import qonduit.Configuration;
import qonduit.api.response.QonduitException;

public class DataStore {

    private final AccumuloClient client;

    public DataStore(Configuration conf) throws QonduitException {

        try {
            Configuration.Accumulo accumuloConf = conf.getAccumulo();
            client = Accumulo.newClient().to(accumuloConf.getInstanceName(), accumuloConf.getZookeepers())
                    .as(accumuloConf.getUsername(), new PasswordToken(accumuloConf.getPassword())).build();
        } catch (Exception e) {
            throw new QonduitException(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), "Error creating DataStoreImpl",
                    e.getMessage(), e);
        }
    }

    public AccumuloClient getConnector() {
        return client;
    }

}
