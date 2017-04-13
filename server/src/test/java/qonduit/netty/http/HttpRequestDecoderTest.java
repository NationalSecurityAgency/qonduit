package qonduit.netty.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import qonduit.Configuration;
import qonduit.api.response.QonduitException;
import qonduit.auth.AuthCache;
import qonduit.netty.Constants;
import qonduit.netty.http.HttpRequestDecoder;
import qonduit.test.TestConfiguration;

public class HttpRequestDecoderTest {

    public static class TestHttpQueryDecoder extends HttpRequestDecoder {

        public TestHttpQueryDecoder(Configuration config) {
            super(config);
        }

        @Override
        public void decode(ChannelHandlerContext ctx, FullHttpRequest msg, List<Object> out) throws Exception {
            super.decode(ctx, msg, out);
        }

    }

    private static Configuration config = null;
    private static Configuration anonConfig = null;
    private TestHttpQueryDecoder decoder = null;
    private List<Object> results = new ArrayList<>();
    private static String cookie = null;

    @BeforeClass
    public static void before() throws Exception {
        config = TestConfiguration.createMinimalConfigurationForTest();
        anonConfig = TestConfiguration.createMinimalConfigurationForTest();
        anonConfig.getSecurity().setAllowAnonymousAccess(true);
        cookie = URLEncoder.encode(UUID.randomUUID().toString(), StandardCharsets.UTF_8.name());
        AuthCache.setSessionMaxAge(config);
        AuthCache.getCache().put(cookie, new UsernamePasswordAuthenticationToken("test", "test1"));
    }

    @AfterClass
    public static void after() {
        AuthCache.resetSessionMaxAge();
    }

    @Before
    public void setup() throws Exception {
        results.clear();
    }

    private void addCookie(FullHttpRequest request) {
        request.headers().set(Names.COOKIE, ClientCookieEncoder.STRICT.encode(Constants.COOKIE_NAME, cookie));
    }

    /**
     * Only /login requests are handled
     */
    @Test(expected = QonduitException.class)
    public void testVersionGet() throws Exception {
        decoder = new TestHttpQueryDecoder(config);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/version");
        addCookie(request);
        decoder.decode(null, request, results);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("VersionRequest", results.iterator().next().getClass().getSimpleName());
    }

    /**
     * Only /login requests are handled
     */
    @Test(expected = QonduitException.class)
    public void testVersionPost() throws Exception {
        decoder = new TestHttpQueryDecoder(config);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/version");
        addCookie(request);
        decoder.decode(null, request, results);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("VersionRequest", results.iterator().next().getClass().getSimpleName());
    }

}
