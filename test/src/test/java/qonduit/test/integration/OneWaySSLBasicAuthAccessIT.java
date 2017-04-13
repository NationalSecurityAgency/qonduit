package qonduit.test.integration;

import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;

import java.io.OutputStream;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.security.Authorizations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import qonduit.Server;
import qonduit.api.request.auth.BasicAuthLoginRequest;
import qonduit.auth.AuthCache;
import qonduit.netty.Constants;
import qonduit.test.IntegrationTest;
import qonduit.util.JsonUtil;

/**
 *
 * Tests that OneWay SSL without anonymous access works.
 *
 */
@Category(IntegrationTest.class)
public class OneWaySSLBasicAuthAccessIT extends OneWaySSLBase {

    protected HttpsURLConnection getUrlConnection(String username, String password, URL url) throws Exception {
        HttpsURLConnection.setDefaultSSLSocketFactory(getSSLSocketFactory());
        URL loginURL = new URL(url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/login");
        HttpsURLConnection con = (HttpsURLConnection) loginURL.openConnection();
        con.setHostnameVerifier((host, session) -> true);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        BasicAuthLoginRequest request = new BasicAuthLoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        byte[] requestJSON = JsonUtil.getObjectMapper().writeValueAsBytes(request);
        con.setRequestProperty("Content-Length", String.valueOf(requestJSON.length));
        OutputStream wr = con.getOutputStream();
        wr.write(requestJSON);
        int responseCode = con.getResponseCode();
        if (401 == responseCode) {
            throw new UnauthorizedUserException();
        }
        Assert.assertEquals(200, responseCode);
        List<String> cookies = con.getHeaderFields().get(Names.SET_COOKIE);
        Assert.assertEquals(1, cookies.size());
        Cookie sessionCookie = ClientCookieDecoder.STRICT.decode(cookies.get(0));
        Assert.assertEquals(Constants.COOKIE_NAME, sessionCookie.name());
        con = (HttpsURLConnection) url.openConnection();
        con.setRequestProperty(Names.COOKIE, sessionCookie.name() + "=" + sessionCookie.value());
        con.setHostnameVerifier((host, session) -> true);
        return con;
    }

    @Before
    public void setup() throws Exception {
        Connector con = mac.getConnector("root", "secret");
        con.securityOperations().changeUserAuthorizations("root", new Authorizations("A", "B", "C", "D", "E", "F"));
    }

    @After
    public void tearDown() throws Exception {
        AuthCache.resetSessionMaxAge();
    }

    @Test
    public void testBasicAuthLogin() throws Exception {
        final Server s = new Server(conf);
        s.run();
        try {
            getUrlConnection("test", "test1", new URL("https://localhost:54322/login"));
        } finally {
            s.shutdown();
        }
    }

    @Test(expected = UnauthorizedUserException.class)
    public void testBasicAuthLoginFailure() throws Exception {
        final Server s = new Server(conf);
        s.run();
        try {
            getUrlConnection("test", "test2", new URL("https://localhost:54322/login"));
        } finally {
            s.shutdown();
        }
    }

}
