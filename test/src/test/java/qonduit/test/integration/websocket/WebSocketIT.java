package qonduit.test.integration.websocket;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static org.junit.Assert.assertEquals;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.security.Authorizations;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import qonduit.Server;
import qonduit.auth.AuthCache;
import qonduit.netty.Constants;
import qonduit.operations.version.VersionRequest;
import qonduit.operations.version.VersionResponse;
import qonduit.serialize.JsonSerializer;
import qonduit.test.IntegrationTest;
import qonduit.test.integration.OneWaySSLBase;

@Category(IntegrationTest.class)
public class WebSocketIT extends OneWaySSLBase {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketIT.class);
    private static final int WS_PORT = 54323;
    private static final URI LOCATION;
    static {
        try {
            LOCATION = new URI("wss://127.0.0.1:" + WS_PORT + "/websocket");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error creating uri for wss://127.0.0.1:" + WS_PORT + "/websocket", e);
        }
    }

    private static class ClientHandler extends SimpleChannelInboundHandler<Object> {

        private static final Logger LOG = LoggerFactory.getLogger(ClientHandler.class);
        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;
        private List<byte[]> responses = new ArrayList<>();
        private volatile boolean connected = false;

        public ClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            LOG.info("Client connected.");
            handshaker.handshake(ctx.channel());
            this.connected = true;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            LOG.info("Client disconnected.");
            this.connected = false;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            LOG.error("Error", cause);
            if (!this.handshakeFuture.isDone()) {
                this.handshakeFuture.setFailure(cause);
            }
            ctx.close();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            handshakeFuture = ctx.newPromise();
        }

        public List<byte[]> getResponses() {
            List<byte[]> result = null;
            synchronized (responses) {
                result = new ArrayList<>(responses);
                responses.clear();
            }
            return result;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            LOG.info("Received msg: {}", msg);
            if (!this.handshaker.isHandshakeComplete()) {
                this.handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
                LOG.info("Client connected.");
                this.connected = true;
                this.handshakeFuture.setSuccess();
                return;
            }
            if (msg instanceof FullHttpResponse) {
                throw new IllegalStateException("Unexpected response: " + msg.toString());
            }
            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                synchronized (responses) {
                    responses.add(((TextWebSocketFrame) frame).text().getBytes(StandardCharsets.UTF_8));
                }
            } else if (frame instanceof BinaryWebSocketFrame) {
                ByteBuf buf = frame.content();
                byte[] b = new byte[buf.readableBytes()];
                buf.readBytes(b);
                synchronized (responses) {
                    responses.add(b);
                }
            } else if (frame instanceof PingWebSocketFrame) {
                LOG.info("Returning pong message");
                ctx.writeAndFlush(new PongWebSocketFrame());
            } else if (frame instanceof CloseWebSocketFrame) {
                LOG.info("Received message from server to close the channel.");
                ctx.close();
            } else {
                LOG.warn("Unhandled frame type received: " + frame.getClass());
            }
        }

        public boolean isConnected() {
            return connected;
        }

    }

    @AfterClass
    public static void after() {
        AuthCache.resetSessionMaxAge();
    }

    private EventLoopGroup group = null;
    private Channel ch = null;
    private ClientHandler handler = null;
    private Server s = null;
    private String sessionId = null;
    private UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("test", "test1");

    @Before
    public void setup() throws Exception {
        s = new Server(conf);
        s.run();

        Connector con = mac.getConnector("root", "secret");
        con.securityOperations().changeUserAuthorizations("root", new Authorizations("A", "B", "C", "D", "E", "F"));

        this.sessionId = UUID.randomUUID().toString();
        AuthCache.getCache().put(sessionId, token);
        group = new NioEventLoopGroup();
        SslContext ssl = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        String cookieVal = ClientCookieEncoder.STRICT.encode(Constants.COOKIE_NAME, sessionId);
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add(Names.COOKIE, cookieVal);

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(LOCATION,
                WebSocketVersion.V13, (String) null, false, headers);
        handler = new ClientHandler(handshaker);
        Bootstrap boot = new Bootstrap();
        boot.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast("ssl", ssl.newHandler(ch.alloc(), "127.0.0.1", WS_PORT));
                ch.pipeline().addLast(new HttpClientCodec());
                ch.pipeline().addLast(new HttpObjectAggregator(8192));
                ch.pipeline().addLast(handler);
            }
        });
        ch = boot.connect("127.0.0.1", WS_PORT).sync().channel();
        // Wait until handshake is complete
        while (!handshaker.isHandshakeComplete()) {
            sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
            LOG.debug("Waiting for Handshake to complete");
        }
    }

    @Test
    public void testVersion() throws Exception {
        try {
            String uuid = UUID.randomUUID().toString();
            VersionRequest request = new VersionRequest();
            request.setRequestId(uuid);
            ch.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(JsonSerializer.getObjectMapper()
                    .writeValueAsBytes(request))));
            // Confirm receipt of all data sent to this point
            List<byte[]> response = handler.getResponses();
            while (response.size() == 0 && handler.isConnected()) {
                LOG.info("Waiting for web socket response");
                sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
                response = handler.getResponses();
            }
            assertEquals(1, response.size());
            VersionResponse version = JsonSerializer.getObjectMapper()
                    .readValue(response.get(0), VersionResponse.class);
            assertEquals(VersionResponse.VERSION, version.getVersion());
            assertEquals(uuid, version.getRequestId());
        } finally {
            ch.close().sync();
            s.shutdown();
            group.shutdownGracefully();
        }
    }
}
