package qonduit.netty.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.security.Authorizations;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.Configuration;
import qonduit.api.request.AuthenticatedRequest;
import qonduit.api.request.WebSocketRequest;
import qonduit.api.response.QonduitException;
import qonduit.auth.AuthCache;
import qonduit.operation.ErrorResponse;
import qonduit.operation.Operation;
import qonduit.operation.OperationResolver;
import qonduit.store.DataStore;
import qonduit.util.JsonUtil;

public class WebSocketRequestDecoder extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketRequestDecoder.class);

    private final DataStore ds;
    private final Configuration conf;

    public WebSocketRequestDecoder(DataStore ds, Configuration conf) {
        this.ds = ds;
        this.conf = conf;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {

        WebSocketRequest request = null;
        if (msg instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame frame = (BinaryWebSocketFrame) msg;
            ByteBuf buf = frame.content();
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            try {
                request = JsonUtil.getObjectMapper().readValue(b, WebSocketRequest.class);
            } catch (Exception e) {
                LOG.error("Error deserializing web socket request: " + e.getMessage());
                ErrorResponse err = new ErrorResponse();
                err.setErrorMessage(e.getMessage());
                err.setRequest(request);
                ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(JsonUtil.getObjectMapper()
                        .writeValueAsBytes(err))));
                return;
            }
        } else if (msg instanceof TextWebSocketFrame) {
            TextWebSocketFrame frame = (TextWebSocketFrame) msg;
            String content = frame.text();
            request = JsonUtil.getObjectMapper().readValue(content, WebSocketRequest.class);
        }

        if (null != request) {
            LOG.trace("Received WS request {}", request);

            final String sessionId = ctx.channel().attr(WebSocketHttpCookieHandler.SESSION_ID_ATTR).get();
            if (request instanceof AuthenticatedRequest && !StringUtils.isEmpty(sessionId)) {
                LOG.info("Found session id in WebSocket channel, setting sessionId {} on request", sessionId);
                AuthenticatedRequest ar = (AuthenticatedRequest) request;
                ar.setSessionId(sessionId);
            }

            try {
                request.validate();
            } catch (IllegalArgumentException e) {
                LOG.error("Error validating web socket request: " + e.getMessage());
                ErrorResponse err = new ErrorResponse();
                err.setErrorMessage("Error validating web socket request: " + e.getMessage());
                err.setRequest(request);
                ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(JsonUtil.getObjectMapper()
                        .writeValueAsBytes(err))));
                return;
            }
            Authorizations auths = new Authorizations();
            try {
                AuthCache.enforceAccess(conf, sessionId);
                if (!this.conf.getSecurity().isAllowAnonymousAccess()) {
                    auths = AuthCache.getAuthorizations(sessionId);
                }
            } catch (QonduitException e) {
                LOG.error("Error during access enforcment: " + e.getMessage());
                ErrorResponse err = new ErrorResponse();
                err.setErrorMessage("Error during access enforcement: " + e.getMessage());
                err.setRequest(request);
                ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(JsonUtil.getObjectMapper()
                        .writeValueAsBytes(err))));
                return;
            }
            Operation o = OperationResolver.getOperation(request.getOperation());
            if (null == o) {
                LOG.error("Unknown request type: {}", request.getOperation());
                ErrorResponse err = new ErrorResponse();
                err.setErrorMessage("Unknown request type: {}" + request.getOperation());
                err.setRequest(request);
                ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(JsonUtil.getObjectMapper()
                        .writeValueAsBytes(err))));
                return;
            }

            // Execute this operation
            o.init(ctx, ds.getConnector(), auths, request);
            ctx.executor().execute(o);

            // send a websocket ping at half the timeout interval.
            int rate = conf.getWebsocket().getTimeout() / 2;
            final ScheduledFuture<?> ping = ctx.executor().scheduleAtFixedRate(() -> {
                LOG.trace("Sending ping on channel {}", ctx.channel());
                ctx.writeAndFlush(new PingWebSocketFrame());
            }, rate, rate, TimeUnit.SECONDS);

            ctx.channel().closeFuture().addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    ping.cancel(true);
                    o.close();
                }
            });

        } else {
            LOG.error("Unhandled web socket frame type");
            ErrorResponse err = new ErrorResponse();
            err.setErrorMessage("Unhandled web socket frame type, only BinaryWebSocketFrame is supported");
            err.setRequest(request);
            ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(JsonUtil.getObjectMapper()
                    .writeValueAsBytes(err))));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.error("Error caught", cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idle = (IdleStateEvent) evt;
            if (idle.state() == IdleState.READER_IDLE) {
                // We have not read any data from client in a while, let's close
                // the subscriptions for this context.
                LOG.info("Client {} is idle", ctx.channel());
            }
        } else {
            LOG.warn("Received unhandled user event {}", evt);
        }
    }

}
