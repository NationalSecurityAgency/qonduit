package qonduit.test.integration;

import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.JdkSslClientContext;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.io.File;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.security.Authorizations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import qonduit.Configuration;
import qonduit.Server;
import qonduit.auth.AuthCache;
import qonduit.netty.Constants;
import qonduit.test.IntegrationTest;

/**
 *
 * Tests that Two way SSL without anonymous access works using OpenSSL on the
 * server.
 *
 */
@SuppressWarnings("deprecation")
@Category(IntegrationTest.class)
public class TwoWaySSLOpenSSLIT extends QueryBase {

    protected static SelfSignedCertificate serverCert = null;
    protected static File clientTrustStoreFile = null;

    static {
        try {
            serverCert = new SelfSignedCertificate();
            clientTrustStoreFile = serverCert.certificate().getAbsoluteFile();
        } catch (Exception e) {
            throw new RuntimeException("Error creating self signed certificate", e);
        }
    }

    protected SSLSocketFactory getSSLSocketFactory() throws Exception {
        SslContextBuilder builder = SslContextBuilder.forClient();
        builder.applicationProtocolConfig(ApplicationProtocolConfig.DISABLED);
        // Use server cert / key on client side.
        builder.keyManager(serverCert.key(), (String) null, serverCert.cert());
        builder.sslProvider(SslProvider.JDK);
        builder.trustManager(clientTrustStoreFile); // Trust the server cert
        SslContext ctx = builder.build();
        Assert.assertEquals(JdkSslClientContext.class, ctx.getClass());
        JdkSslContext jdk = (JdkSslContext) ctx;
        SSLContext jdkSslContext = jdk.context();
        return jdkSslContext.getSocketFactory();
    }

    protected static void setupSSL(Configuration config) throws Exception {
        config.getSecurity().getSsl().setCertificateFile(serverCert.certificate().getAbsolutePath());
        config.getSecurity().getSsl().setKeyFile(serverCert.privateKey().getAbsolutePath());
        // Needed for 2way SSL
        config.getSecurity().getSsl().setTrustStoreFile(serverCert.certificate().getAbsolutePath());
        config.getSecurity().getSsl().setUseOpenssl(false);
        config.getSecurity().getSsl().setUseGeneratedKeypair(false);
        config.getSecurity().setAllowAnonymousAccess(false);
    }

    @Before
    public void configureSSL() throws Exception {
        setupSSL(conf);
    }

    protected HttpsURLConnection getUrlConnection(URL url) throws Exception {
        // Username and password not used in 2way SSL case
        return getUrlConnection(null, null, url);
    }

    protected HttpsURLConnection getUrlConnection(String username, String password, URL url) throws Exception {
        HttpsURLConnection.setDefaultSSLSocketFactory(getSSLSocketFactory());
        URL loginURL = new URL(url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/login");
        HttpsURLConnection con = (HttpsURLConnection) loginURL.openConnection();
        con.setHostnameVerifier(new HostnameVerifier() {

            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        });
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        con.connect();
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
        con.setHostnameVerifier(new HostnameVerifier() {

            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        });
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
            getUrlConnection(new URL("https://localhost:54322/login"));
        } finally {
            s.shutdown();
        }
    }

}
