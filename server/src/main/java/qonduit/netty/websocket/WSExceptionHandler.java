package qonduit.netty.websocket;

import qonduit.api.response.QonduitException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;

public class WSExceptionHandler extends SimpleChannelInboundHandler<QonduitException> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, QonduitException e) throws Exception {
        ctx.writeAndFlush(new CloseWebSocketFrame(1008, e.getMessage()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) throws Exception {
        ctx.writeAndFlush(new CloseWebSocketFrame(1008, e.getMessage()));
    }

}
