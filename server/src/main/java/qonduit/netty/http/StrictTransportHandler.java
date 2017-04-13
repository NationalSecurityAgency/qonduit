package qonduit.netty.http;

import qonduit.Configuration;
import qonduit.api.response.StrictTransportResponse;
import qonduit.api.response.QonduitException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;

public class StrictTransportHandler extends SimpleChannelInboundHandler<StrictTransportResponse> {

    public static final String HSTS_HEADER_NAME = "Strict-Transport-Security";
    private String hstsMaxAge = "max-age=";

    public StrictTransportHandler(Configuration conf) {
        long maxAge = conf.getHttp().getStrictTransportMaxAge();
        hstsMaxAge = "max-age=" + maxAge;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, StrictTransportResponse msg) throws Exception {
        QonduitException e = new QonduitException(HttpResponseStatus.NOT_FOUND.code(),
                "Returning HTTP Strict Transport Security response", null, null);
        e.addResponseHeader(HSTS_HEADER_NAME, hstsMaxAge);
        // Don't call sendHttpError from here, throw an error instead and let
        // the exception handler catch it.
        throw e;
    }

}
