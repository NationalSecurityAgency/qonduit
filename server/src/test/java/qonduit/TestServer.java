package qonduit;

import qonduit.Configuration;
import qonduit.Server;
import qonduit.test.TestCaptureRequestHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.ssl.SslContext;

public class TestServer extends Server {

    private final TestCaptureRequestHandler httpRequests = new TestCaptureRequestHandler();

    public TestServer(Configuration conf) throws Exception {
        super(conf);
    }

    @Override
    protected ChannelHandler setupHttpChannel(Configuration config, SslContext sslCtx) {
        return new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast("ssl", sslCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast("decompressor", new HttpContentDecompressor());
                ch.pipeline().addLast("decoder", new HttpRequestDecoder());
                ch.pipeline().addLast("aggregator", new HttpObjectAggregator(8192));
                ch.pipeline().addLast("queryDecoder", new qonduit.netty.http.HttpRequestDecoder(config));
                ch.pipeline().addLast("capture", httpRequests);
            }
        };
    }

    public TestCaptureRequestHandler getHttpRequests() {
        return httpRequests;
    }

}
