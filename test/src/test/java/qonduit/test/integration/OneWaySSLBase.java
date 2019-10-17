package qonduit.test.integration;

import java.io.File;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import qonduit.Configuration;
import qonduit.auth.AuthCache;

/**
 * Base test class for SSL with anonymous access
 */
public class OneWaySSLBase extends QueryBase {

    protected static File clientTrustStoreFile = null;

    protected SSLSocketFactory getSSLSocketFactory() throws Exception {
        SslContextBuilder builder = SslContextBuilder.forClient();
        builder.applicationProtocolConfig(ApplicationProtocolConfig.DISABLED);
        builder.sslProvider(SslProvider.JDK);
        builder.trustManager(clientTrustStoreFile); // Trust the server cert
        SslContext ctx = builder.build();
        Assert.assertTrue(ctx.isClient());
        JdkSslContext jdk = (JdkSslContext) ctx;
        SSLContext jdkSslContext = jdk.context();
        return jdkSslContext.getSocketFactory();
    }

    protected static void setupSSL(Configuration config) throws Exception {
        SelfSignedCertificate serverCert = new SelfSignedCertificate();
        config.getSecurity().getSsl().setCertificateFile(serverCert.certificate().getAbsolutePath());
        clientTrustStoreFile = serverCert.certificate().getAbsoluteFile();
        config.getSecurity().getSsl().setKeyFile(serverCert.privateKey().getAbsolutePath());
        config.getSecurity().getSsl().setUseOpenssl(false);
        config.getSecurity().getSsl().setUseGeneratedKeypair(false);
        config.getSecurity().setAllowAnonymousAccess(true);
    }

    @Before
    public void configureSSL() throws Exception {
        setupSSL(conf);
    }

    @Override
    protected HttpsURLConnection getUrlConnection(String username, String password, URL url) throws Exception {
        // No username/password needed for anonymous access
        return getUrlConnection(url);
    }

    @Override
    protected HttpsURLConnection getUrlConnection(URL url) throws Exception {
        HttpsURLConnection.setDefaultSSLSocketFactory(getSSLSocketFactory());
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setHostnameVerifier((host, session) -> true);
        return con;
    }

    @After
    public void tearDown() throws Exception {
        AuthCache.resetSessionMaxAge();
    }

}
