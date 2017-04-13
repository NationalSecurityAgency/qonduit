package qonduit.test.integration;

import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import qonduit.Server;
import qonduit.test.IntegrationTest;

/**
 *
 * Tests that OneWay SSL with anonymous access works.
 *
 */
@Category(IntegrationTest.class)
public class OneWaySSLAnonAccessIT extends OneWaySSLBase {

    @Test
    public void testAnonymousAccess() throws Exception {
        final Server s = new Server(conf);
        s.run();
        try {
            HttpsURLConnection con = getUrlConnection(new URL("https://localhost:54322/login"));
            /*
             * Anonymous access is enabled in the server for this test, but
             * since the client does not send client certificate information,
             * there is no way to validate the user credentials and a 401
             * response code is returned.
             */
            Assert.assertEquals(401, con.getResponseCode());
        } finally {
            s.shutdown();
        }
    }

}
