package qonduit.store;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;

import org.apache.accumulo.core.client.ClientConfiguration;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.Instance;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.commons.configuration.BaseConfiguration;

import qonduit.Configuration;
import qonduit.api.response.QonduitException;

public class DataStore {

    private final Connector connector;

    public DataStore(Configuration conf) throws QonduitException {

        try {
            final BaseConfiguration apacheConf = new BaseConfiguration();
            Configuration.Accumulo accumuloConf = conf.getAccumulo();
            apacheConf.setProperty("instance.name", accumuloConf.getInstanceName());
            apacheConf.setProperty("instance.zookeeper.host", accumuloConf.getZookeepers());
            final ClientConfiguration aconf = new ClientConfiguration(Collections.singletonList(apacheConf));
            final Instance instance = new ZooKeeperInstance(aconf);
            connector = instance
                    .getConnector(accumuloConf.getUsername(), new PasswordToken(accumuloConf.getPassword()));
        } catch (Exception e) {
            throw new QonduitException(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), "Error creating DataStoreImpl",
                    e.getMessage(), e);
        }
    }

    public Connector getConnector() {
        return connector;
    }

}
