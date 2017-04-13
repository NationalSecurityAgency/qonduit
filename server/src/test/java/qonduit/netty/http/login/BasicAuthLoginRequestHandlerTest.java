package qonduit.netty.http.login;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import qonduit.Configuration;
import qonduit.api.BasicAuthLogin;
import qonduit.api.request.auth.BasicAuthLoginRequest;
import qonduit.auth.AuthCache;
import qonduit.netty.Constants;
import qonduit.netty.http.HttpRequestDecoder;
import qonduit.netty.http.auth.BasicAuthLoginRequestHandler;
import qonduit.serialize.JsonSerializer;
import qonduit.test.CaptureChannelHandlerContext;
import qonduit.test.TestConfiguration;

public class BasicAuthLoginRequestHandlerTest {

    private static class TestHttpQueryDecoder extends HttpRequestDecoder {

        public TestHttpQueryDecoder(Configuration config) {
            super(config);
        }

        @Override
        public void decode(ChannelHandlerContext ctx, FullHttpRequest msg, List<Object> out) throws Exception {
            super.decode(ctx, msg, out);
        }

    }

    private List<Object> results = new ArrayList<>();

    @BeforeClass
    public static void before() throws Exception {
        AuthCache.setSessionMaxAge(TestConfiguration.createMinimalConfigurationForTest());
    }

    @AfterClass
    public static void after() throws Exception {
        AuthCache.resetSessionMaxAge();
    }

    @Before
    public void setup() throws Exception {
        results.clear();
    }

    @Test
    public void testBasicAuthentication() throws Exception {
        Configuration config = TestConfiguration.createMinimalConfigurationForTest();

        BasicAuthLogin auth = new BasicAuthLogin();
        auth.setUsername("test");
        auth.setPassword("test1");
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/login");
        request.content().writeBytes(JsonSerializer.getObjectMapper().writeValueAsBytes(auth));

        TestHttpQueryDecoder decoder = new TestHttpQueryDecoder(config);
        decoder.decode(null, request, results);
        Assert.assertEquals(1, results.size());
        Object result = results.iterator().next();
        Assert.assertEquals(BasicAuthLoginRequest.class, result.getClass());

        BasicAuthLoginRequestHandler handler = new BasicAuthLoginRequestHandler(config);
        CaptureChannelHandlerContext ctx = new CaptureChannelHandlerContext();
        handler.channelRead(ctx, result);
        Assert.assertNotNull(ctx.msg);
        Assert.assertTrue(ctx.msg instanceof DefaultFullHttpResponse);
        DefaultFullHttpResponse response = (DefaultFullHttpResponse) ctx.msg;
        Assert.assertEquals(HttpResponseStatus.OK, response.getStatus());
        Assert.assertTrue(response.headers().contains(Names.CONTENT_TYPE));
        Assert.assertEquals(Constants.JSON_TYPE, response.headers().get(Names.CONTENT_TYPE));
        Assert.assertTrue(response.headers().contains(Names.SET_COOKIE));
        Cookie c = ClientCookieDecoder.STRICT.decode(response.headers().get(Names.SET_COOKIE));
        Assert.assertEquals(TestConfiguration.HTTP_ADDRESS_DEFAULT, c.domain());
        Assert.assertEquals(86400, c.maxAge());
        Assert.assertTrue(c.isHttpOnly());
        Assert.assertTrue(c.isSecure());
        Assert.assertEquals(Constants.COOKIE_NAME, c.name());
        UUID.fromString(c.value());
    }

    @Test
    public void testBasicAuthenticationFailure() throws Exception {
        Configuration config = TestConfiguration.createMinimalConfigurationForTest();

        BasicAuthLogin auth = new BasicAuthLogin();
        auth.setUsername("test");
        auth.setPassword("test2");
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/login");
        request.content().writeBytes(JsonSerializer.getObjectMapper().writeValueAsBytes(auth));

        TestHttpQueryDecoder decoder = new TestHttpQueryDecoder(config);
        decoder.decode(null, request, results);
        Assert.assertEquals(1, results.size());
        Object result = results.iterator().next();
        Assert.assertEquals(BasicAuthLoginRequest.class, result.getClass());

        BasicAuthLoginRequestHandler handler = new BasicAuthLoginRequestHandler(config);
        CaptureChannelHandlerContext ctx = new CaptureChannelHandlerContext();
        handler.channelRead(ctx, result);
        Assert.assertNotNull(ctx.msg);
        Assert.assertTrue(ctx.msg instanceof DefaultFullHttpResponse);
        DefaultFullHttpResponse response = (DefaultFullHttpResponse) ctx.msg;
        Assert.assertEquals(HttpResponseStatus.UNAUTHORIZED, response.getStatus());
        Assert.assertTrue(response.headers().contains(Names.CONTENT_TYPE));
        Assert.assertEquals(Constants.JSON_TYPE, response.headers().get(Names.CONTENT_TYPE));
    }

}
