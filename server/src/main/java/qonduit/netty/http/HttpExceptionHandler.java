package qonduit.netty.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.api.response.QonduitException;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;

@Sharable
public class HttpExceptionHandler extends SimpleChannelInboundHandler<QonduitException> implements HttpHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpExceptionHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, QonduitException msg) throws Exception {
        this.sendHttpError(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.error("Unhandled exception in pipeline", cause);
        if (cause instanceof QonduitException) {
            this.sendHttpError(ctx, (QonduitException) cause);
        } else if (null != cause.getCause() && cause.getCause() instanceof QonduitException) {
            this.sendHttpError(ctx, (QonduitException) cause.getCause());
        } else {
            QonduitException e = new QonduitException(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(),
                    cause.getMessage(), "");
            this.sendHttpError(ctx, e);
        }
    }

}
