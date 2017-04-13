package qonduit.test.integration.http;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import qonduit.Server;
import qonduit.netty.http.StrictTransportHandler;
import qonduit.test.IntegrationTest;
import qonduit.test.integration.OneWaySSLBase;

@Category(IntegrationTest.class)
public class HTTPStrictTransportSecurityIT extends OneWaySSLBase {

    private static Server s = null;

    @Before
    public void before() throws Exception {
        super.configureSSL();
        s = new Server(conf);
        s.run();
    }

    @After
    public void after() throws Exception {
        if (null != s) {
            s.shutdown();
        }
    }

    @Test
    public void testHttpRequestGet() throws Exception {

        RequestConfig.Builder req = RequestConfig.custom();
        req.setConnectTimeout(5000);
        req.setConnectionRequestTimeout(5000);
        req.setRedirectsEnabled(false);
        req.setSocketTimeout(5000);
        req.setExpectContinueEnabled(false);

        HttpGet get = new HttpGet("http://127.0.0.1:54322/login");
        get.setConfig(req.build());

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setDefaultMaxPerRoute(5);

        HttpClientBuilder builder = HttpClients.custom();
        builder.disableAutomaticRetries();
        builder.disableRedirectHandling();
        builder.setConnectionTimeToLive(5, TimeUnit.SECONDS);
        builder.setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE);
        builder.setConnectionManager(cm);
        CloseableHttpClient client = builder.build();

        String s = client.execute(get, new ResponseHandler<String>() {

            @Override
            public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                assertEquals(301, response.getStatusLine().getStatusCode());
                return "success";
            }

        });
        assertEquals("success", s);

    }

    @Test
    public void testHSTSRequestGet() throws Exception {
        String secureMe = "https://127.0.0.1:54322/secure-me";
        URL url = new URL(secureMe);
        HttpsURLConnection con = getUrlConnection(url);
        int responseCode = con.getResponseCode();
        assertEquals(404, responseCode);
        assertEquals("max-age=604800", con.getHeaderField(StrictTransportHandler.HSTS_HEADER_NAME));
    }

}
