package qonduit.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.Configuration;
import qonduit.api.request.auth.BasicAuthLoginRequest;
import qonduit.api.request.auth.X509LoginRequest;
import qonduit.api.response.QonduitException;
import qonduit.api.response.StrictTransportResponse;
import qonduit.netty.Constants;

public class HttpRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> implements HttpHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRequestDecoder.class);
    private static final String LOG_RECEIVED_REQUEST = "Received HTTP request {}";
    private static final String NO_AUTHORIZATIONS = "";

    private final Configuration conf;
    private boolean anonymousAccessAllowed = false;
    private final String nonSecureRedirectAddress;

    public HttpRequestDecoder(Configuration config) {
        this.conf = config;
        this.anonymousAccessAllowed = conf.getSecurity().isAllowAnonymousAccess();
        this.nonSecureRedirectAddress = conf.getHttp().getRedirectPath();
    }

    public static String getSessionId(FullHttpRequest msg, boolean anonymousAccessAllowed) {
        final StringBuilder buf = new StringBuilder();
        msg.headers().getAll(Names.COOKIE).forEach(h -> {
            ServerCookieDecoder.STRICT.decode(h).forEach(c -> {
                if (c.name().equals(Constants.COOKIE_NAME)) {
                    if (buf.length() == 0) {
                        buf.append(c.value());
                    }
                }
            });
        });
        String sessionId = buf.toString();
        if (sessionId.length() == 0 && anonymousAccessAllowed) {
            sessionId = NO_AUTHORIZATIONS;
        } else if (sessionId.length() == 0) {
            sessionId = null;
        }
        return sessionId;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest msg, List<Object> out) throws Exception {

        LOG.trace(LOG_RECEIVED_REQUEST, msg);

        final String uri = msg.getUri();
        final QueryStringDecoder decoder = new QueryStringDecoder(uri);
        if (decoder.path().equals(nonSecureRedirectAddress)) {
            out.add(new StrictTransportResponse());
            return;
        }

        final String sessionId = getSessionId(msg, this.anonymousAccessAllowed);
        LOG.trace("SessionID: " + sessionId);

        if (decoder.path().equals("/login")) {
            if (msg.getMethod().equals(HttpMethod.GET)) {
                out.add(new X509LoginRequest());
            } else if (msg.getMethod().equals(HttpMethod.POST)) {
                ByteBuf body = msg.content();
                byte[] content = null;
                if (null != body) {
                    content = new byte[body.readableBytes()];
                    body.readBytes(content);
                }
                BasicAuthLoginRequest request = BasicAuthLoginRequest.parseBody(content);
                request.validate();
                out.add(request);
            } else {
                QonduitException e = new QonduitException(HttpResponseStatus.METHOD_NOT_ALLOWED.code(),
                        "unhandled method type", "");
                e.addResponseHeader(Names.ALLOW, HttpMethod.GET.name() + "," + HttpMethod.POST.name());
                LOG.warn("Unhandled HTTP request type {}", msg.getMethod());
                throw e;
            }
        } else {
            QonduitException e = new QonduitException(HttpResponseStatus.SEE_OTHER.code(), "Unknown request path", "");
            LOG.warn("Unknown request path {}", decoder.path());
            throw e;
        }

    }

}
