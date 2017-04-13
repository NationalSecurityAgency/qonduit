package qonduit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.OpenSslServerSessionContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import qonduit.auth.AuthCache;
import qonduit.auth.VisibilityCache;
import qonduit.netty.http.HttpExceptionHandler;
import qonduit.netty.http.NonSecureHttpHandler;
import qonduit.netty.http.StrictTransportHandler;
import qonduit.netty.http.auth.BasicAuthLoginRequestHandler;
import qonduit.netty.http.auth.X509LoginRequestHandler;
import qonduit.netty.websocket.WSExceptionHandler;
import qonduit.netty.websocket.WebSocketHttpCookieHandler;
import qonduit.netty.websocket.WebSocketRequestDecoder;
import qonduit.operation.OperationResolver;
import qonduit.store.DataStore;
import qonduit.store.DataStoreFactory;

public class Server {

    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final int EPOLL_MIN_MAJOR_VERSION = 2;
    private static final int EPOLL_MIN_MINOR_VERSION = 6;
    private static final int EPOLL_MIN_PATCH_VERSION = 32;
    private static final String OS_NAME = "os.name";
    private static final String OS_VERSION = "os.version";
    private static final String WS_PATH = "/websocket";

    protected static final CountDownLatch LATCH = new CountDownLatch(1);
    static ConfigurableApplicationContext applicationContext;

    private final Configuration config;
    private EventLoopGroup httpWorkerGroup = null;
    private EventLoopGroup httpBossGroup = null;
    private EventLoopGroup wsWorkerGroup = null;
    private EventLoopGroup wsBossGroup = null;
    protected Channel httpChannelHandle = null;
    protected Channel wsChannelHandle = null;
    protected DataStore dataStore = null;
    protected volatile boolean shutdown = false;

    private static boolean useEpoll() {

        // Should we just return true if this is Linux and if we get an error
        // during Epoll
        // setup handle it there?
        final String os = SystemPropertyUtil.get(OS_NAME).toLowerCase().trim();
        final String[] version = SystemPropertyUtil.get(OS_VERSION).toLowerCase().trim().split("\\.");
        if (os.startsWith("linux") && version.length >= 3) {
            final int major = Integer.parseInt(version[0]);
            if (major > EPOLL_MIN_MAJOR_VERSION) {
                return true;
            } else if (major == EPOLL_MIN_MAJOR_VERSION) {
                final int minor = Integer.parseInt(version[1]);
                if (minor > EPOLL_MIN_MINOR_VERSION) {
                    return true;
                } else if (minor == EPOLL_MIN_MINOR_VERSION) {
                    final int patch = Integer.parseInt(version[2].substring(0, 2));
                    return patch >= EPOLL_MIN_PATCH_VERSION;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static void fatal(String msg, Throwable t) {
        LOG.error(msg, t);
        LATCH.countDown();
    }

    public static void main(String[] args) throws Exception {

        Configuration conf = initializeConfiguration(args);

        Server s = new Server(conf);
        try {
            s.run();
            LATCH.await();
        } catch (final InterruptedException e) {
            LOG.info("Server shutting down.");
        } catch (Exception e) {
            LOG.error("Error running server.", e);
        } finally {
            try {
                s.shutdown();
            } catch (Exception e) {
                System.exit(1);
            }
        }
    }

    protected static Configuration initializeConfiguration(String[] args) {
        applicationContext = new SpringApplicationBuilder(SpringBootstrap.class).web(false).run(args);
        return applicationContext.getBean(Configuration.class);
    }

    private void shutdownHook() {

        final Runnable shutdownRunner = () -> {
            if (!shutdown) {
                shutdown();
            }
        };
        final Thread hook = new Thread(shutdownRunner, "shutdown-hook-thread");
        Runtime.getRuntime().addShutdownHook(hook);
    }

    public void shutdown() {
        List<ChannelFuture> channelFutures = new ArrayList<>();

        if (httpChannelHandle != null) {
            LOG.info("Closing httpChannelHandle");
            channelFutures.add(httpChannelHandle.close());
        }

        if (wsChannelHandle != null) {
            LOG.info("Closing wsChannelHandle");
            channelFutures.add(wsChannelHandle.close());
        }

        // wait for the channels to shutdown
        channelFutures.forEach(f -> {
            try {
                f.get();
            } catch (final Exception e) {
                LOG.error("Error while shutting down channel: {}", f.channel().config());
                LOG.error("{}", e.getMessage());
                e.printStackTrace();
            }
        });

        Integer quietPeriod = config.getServer().getShutdownQuietPeriod();
        List<Future<?>> groupFutures = new ArrayList<>();
        if (httpBossGroup != null) {
            LOG.info("Shutting down httpBossGroup");
            groupFutures.add(httpBossGroup.shutdownGracefully(quietPeriod, 10, TimeUnit.SECONDS));
        }

        if (httpWorkerGroup != null) {
            LOG.info("Shutting down httpWorkerGroup");
            groupFutures.add(httpWorkerGroup.shutdownGracefully(quietPeriod, 10, TimeUnit.SECONDS));
        }

        if (wsBossGroup != null) {
            LOG.info("Shutting down wsBossGroup");
            groupFutures.add(wsBossGroup.shutdownGracefully(quietPeriod, 10, TimeUnit.SECONDS));
        }

        if (wsWorkerGroup != null) {
            LOG.info("Shutting down wsWorkerGroup");
            groupFutures.add(wsWorkerGroup.shutdownGracefully(quietPeriod, 10, TimeUnit.SECONDS));
        }

        groupFutures.parallelStream().forEach(f -> {
            try {
                f.get();
            } catch (final Exception e) {
                LOG.error("Error while shutting down group: {}", f.toString());
                LOG.error("{}", e.getMessage());
                e.printStackTrace();
            }
        });

        if (applicationContext != null) {
            LOG.info("Closing applicationContext");
            applicationContext.close();
        }
        this.shutdown = true;
        LOG.info("Server shut down.");
    }

    public Server(Configuration conf) throws Exception {

        this.config = conf;
    }

    public void run() throws Exception {

        new OperationResolver();
        dataStore = DataStoreFactory.create(config);
        // initialize the auth cache
        AuthCache.setSessionMaxAge(config);
        // Initialize the VisibilityCache
        VisibilityCache.init(config);
        final boolean useEpoll = useEpoll();
        Class<? extends ServerSocketChannel> channelClass;
        if (useEpoll) {
            httpWorkerGroup = new EpollEventLoopGroup();
            httpBossGroup = new EpollEventLoopGroup();
            wsWorkerGroup = new EpollEventLoopGroup();
            wsBossGroup = new EpollEventLoopGroup();
            channelClass = EpollServerSocketChannel.class;
        } else {
            httpWorkerGroup = new NioEventLoopGroup();
            httpBossGroup = new NioEventLoopGroup();
            wsWorkerGroup = new NioEventLoopGroup();
            wsBossGroup = new NioEventLoopGroup();
            channelClass = NioServerSocketChannel.class;
        }
        LOG.info("Using channel class {}", channelClass.getSimpleName());

        final int httpPort = config.getHttp().getPort();
        final String httpIp = config.getHttp().getIp();
        SslContext sslCtx = createSSLContext(config);
        if (sslCtx instanceof OpenSslServerContext) {
            OpenSslServerContext openssl = (OpenSslServerContext) sslCtx;
            String application = "Qonduit_" + httpPort;
            OpenSslServerSessionContext opensslCtx = openssl.sessionContext();
            opensslCtx.setSessionCacheEnabled(true);
            opensslCtx.setSessionCacheSize(128);
            opensslCtx.setSessionIdContext(application.getBytes(StandardCharsets.UTF_8));
            opensslCtx.setSessionTimeout(config.getSecurity().getSessionMaxAge());
        }
        final ServerBootstrap httpServer = new ServerBootstrap();
        httpServer.group(httpBossGroup, httpWorkerGroup);
        httpServer.channel(channelClass);
        httpServer.handler(new LoggingHandler());
        httpServer.childHandler(setupHttpChannel(config, sslCtx));
        httpServer.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        httpChannelHandle = httpServer.bind(httpIp, httpPort).sync().channel();
        final String httpAddress = ((InetSocketAddress) httpChannelHandle.localAddress()).getAddress().getHostAddress();

        final int wsPort = config.getWebsocket().getPort();
        final String wsIp = config.getWebsocket().getIp();
        final ServerBootstrap wsServer = new ServerBootstrap();
        wsServer.group(wsBossGroup, wsWorkerGroup);
        wsServer.channel(channelClass);
        wsServer.handler(new LoggingHandler());
        wsServer.childHandler(setupWSChannel(sslCtx, config, dataStore));
        wsServer.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        wsChannelHandle = wsServer.bind(wsIp, wsPort).sync().channel();
        final String wsAddress = ((InetSocketAddress) wsChannelHandle.localAddress()).getAddress().getHostAddress();

        shutdownHook();
        LOG.info("Server started. Listening on {}:{} for HTTP traffic, {}:{} for WebSocket traffic", httpAddress,
                httpPort, wsAddress, wsPort);
    }

    protected SslContext createSSLContext(Configuration config) throws Exception {

        Configuration.Ssl sslCfg = config.getSecurity().getSsl();
        Boolean generate = sslCfg.isUseGeneratedKeypair();
        SslContextBuilder ssl;
        if (generate) {
            LOG.warn("Using generated self signed server certificate");
            Date begin = new Date();
            Date end = new Date(begin.getTime() + 86400000);
            SelfSignedCertificate ssc = new SelfSignedCertificate("localhost", begin, end);
            ssl = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey());
        } else {
            String cert = sslCfg.getCertificateFile();
            String key = sslCfg.getKeyFile();
            String keyPass = sslCfg.getKeyPassword();
            if (null == cert || null == key) {
                throw new IllegalArgumentException("Check your SSL properties, something is wrong.");
            }
            ssl = SslContextBuilder.forServer(new File(cert), new File(key), keyPass);
        }

        ssl.ciphers(sslCfg.getUseCiphers());

        // Can't set to REQUIRE because the CORS pre-flight requests will fail.
        ssl.clientAuth(ClientAuth.OPTIONAL);

        Boolean useOpenSSL = sslCfg.isUseOpenssl();
        if (useOpenSSL) {
            ssl.sslProvider(SslProvider.OPENSSL);
        } else {
            ssl.sslProvider(SslProvider.JDK);
        }
        String trustStore = sslCfg.getTrustStoreFile();
        if (null != trustStore) {
            if (!trustStore.isEmpty()) {
                ssl.trustManager(new File(trustStore));
            }
        }
        return ssl.build();
    }

    protected ChannelHandler setupHttpChannel(Configuration config, SslContext sslCtx) {

        return new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {

                ch.pipeline().addLast("ssl", sslCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast("encoder", new HttpResponseEncoder());
                ch.pipeline().addLast("decoder", new HttpRequestDecoder());
                ch.pipeline().addLast("non-secure", new NonSecureHttpHandler(config));
                ch.pipeline().addLast("compressor", new HttpContentCompressor());
                ch.pipeline().addLast("decompressor", new HttpContentDecompressor());
                ch.pipeline().addLast("aggregator", new HttpObjectAggregator(8192));
                ch.pipeline().addLast("chunker", new ChunkedWriteHandler());
                final Configuration.Cors corsCfg = config.getHttp().getCors();
                final CorsConfig.Builder ccb;
                if (corsCfg.isAllowAnyOrigin()) {
                    ccb = new CorsConfig.Builder();
                } else {
                    ccb = new CorsConfig.Builder(corsCfg.getAllowedOrigins().stream().toArray(String[]::new));
                }
                if (corsCfg.isAllowNullOrigin()) {
                    ccb.allowNullOrigin();
                }
                if (corsCfg.isAllowCredentials()) {
                    ccb.allowCredentials();
                }
                corsCfg.getAllowedMethods().stream().map(HttpMethod::valueOf).forEach(ccb::allowedRequestMethods);
                corsCfg.getAllowedHeaders().forEach(ccb::allowedRequestHeaders);
                CorsConfig cors = ccb.build();
                LOG.trace("Cors configuration: {}", cors);
                ch.pipeline().addLast("cors", new CorsHandler(cors));
                ch.pipeline().addLast("queryDecoder", new qonduit.netty.http.HttpRequestDecoder(config));
                ch.pipeline().addLast("strict", new StrictTransportHandler(config));
                ch.pipeline().addLast("login", new X509LoginRequestHandler(config));
                ch.pipeline().addLast("doLogin", new BasicAuthLoginRequestHandler(config));
                ch.pipeline().addLast("error", new HttpExceptionHandler());
            }
        };
    }

    protected ChannelHandler setupWSChannel(SslContext sslCtx, Configuration conf, DataStore datastore) {
        return new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast("ssl", sslCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast("httpServer", new HttpServerCodec());
                ch.pipeline().addLast("aggregator", new HttpObjectAggregator(8192));
                ch.pipeline().addLast("sessionExtractor", new WebSocketHttpCookieHandler(config));
                ch.pipeline().addLast("idle-handler", new IdleStateHandler(conf.getWebsocket().getTimeout(), 0, 0));
                ch.pipeline().addLast("ws-protocol", new WebSocketServerProtocolHandler(WS_PATH, null, true));
                ch.pipeline().addLast("wsDecoder", new WebSocketRequestDecoder(datastore, config));
                ch.pipeline().addLast("error", new WSExceptionHandler());
            }
        };

    }

}
