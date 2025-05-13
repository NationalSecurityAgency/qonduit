package qonduit.test.integration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.apache.accumulo.minicluster.MiniAccumuloConfig;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.Configuration;
import qonduit.test.TestConfiguration;

/**
 * Base class for integration tests using mini accumulo cluster.
 */
public class MacITBase {

    private static final Logger LOG = LoggerFactory.getLogger(MacITBase.class);

    private static final File tempDir;

    protected static final String MAC_ROOT_USER = "root";
    protected static final String MAC_ROOT_PASSWORD = "secret";

    static {
        try {
            tempDir = Files.createTempDirectory("mac_temp").toFile();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temp directory for mini accumulo cluster");
        }
        tempDir.deleteOnExit();
    }

    protected static MiniAccumuloCluster mac = null;
    protected static Configuration conf = null;

    @BeforeClass
    public static void setupMiniAccumulo() throws Exception {
        if (null == mac) {
            final MiniAccumuloConfig macConfig = new MiniAccumuloConfig(tempDir, MAC_ROOT_PASSWORD);
            mac = new MiniAccumuloCluster(macConfig);
            mac.start();
            conf = TestConfiguration.createMinimalConfigurationForTest();
            conf.getAccumulo().setInstanceName(mac.getInstanceName());
            conf.getAccumulo().setZookeepers(mac.getZooKeepers());
            conf.getSecurity().getSsl().setUseOpenssl(false);
            conf.getSecurity().getSsl().setUseGeneratedKeypair(true);
        } else {
            LOG.info("Mini Accumulo already running.");
        }
    }

    @Before
    public void clearTablesResetConf() throws Exception {
        AccumuloClient client = mac.createAccumuloClient("root", new PasswordToken("secret"));
        client.securityOperations().changeUserAuthorizations("root", new Authorizations("A", "B", "C", "D", "E", "F"));
        client.tableOperations().list().forEach(t -> {
            if (t.startsWith("qonduit")) {
                try {
                    client.tableOperations().delete(t);
                } catch (Exception e) {
                }
            }
        });
        // Reset configuration
        conf = TestConfiguration.createMinimalConfigurationForTest();
        conf.getAccumulo().setInstanceName(mac.getInstanceName());
        conf.getAccumulo().setZookeepers(mac.getZooKeepers());
        conf.getSecurity().getSsl().setUseOpenssl(false);
        conf.getSecurity().getSsl().setUseGeneratedKeypair(true);
    }

}
