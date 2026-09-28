package qonduit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import qonduit.test.TestConfiguration;

public class ServerSSLContextTest {

    private Configuration.Ssl sslConfiguration() {
        Configuration config = TestConfiguration.createMinimalConfigurationForTest();
        Configuration.Ssl ssl = config.getSecurity().getSsl();
        ssl.setUseGeneratedKeypair(false);
        ssl.setCertificateFile("/dev/null");
        ssl.setKeyFile("/dev/null");
        return ssl;
    }

    @Test
    public void testConfiguredTrustStoreIsAccepted() {
        Configuration.Ssl ssl = sslConfiguration();
        ssl.setTrustStoreFile("/dev/null");

        Server.validateSslConfiguration(ssl);
    }

    @Test
    public void testMissingTrustStoreIsRejected() {
        try {
            Server.validateSslConfiguration(sslConfiguration());
            fail("expected a missing trust store to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("trust-store-file"));
        }
    }

    @Test
    public void testEmptyTrustStoreIsRejected() {
        Configuration.Ssl ssl = sslConfiguration();
        ssl.setTrustStoreFile("");

        try {
            Server.validateSslConfiguration(ssl);
            fail("expected an empty trust store to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("trust-store-file"));
        }
    }

    @Test
    public void testMissingCertificateIsStillRejected() {
        Configuration.Ssl ssl = sslConfiguration();
        ssl.setCertificateFile(null);
        ssl.setTrustStoreFile("/dev/null");

        try {
            Server.validateSslConfiguration(ssl);
            fail("expected a missing certificate to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("SSL properties"));
        }
    }
}
