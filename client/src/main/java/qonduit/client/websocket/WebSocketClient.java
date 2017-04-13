package qonduit.client.websocket;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.websocket.DeploymentException;
import javax.websocket.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.api.BasicAuthLogin;
import qonduit.client.http.HttpClient;
import qonduit.serialize.JsonSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Preconditions;

public class WebSocketClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketClient.class);

    private final String hostname;
    private final int httpsPort;
    private final int wssPort;
    private final boolean doLogin;
    private final String username;
    private final String password;
    private final boolean hostVerificationEnabled;
    private final int bufferSize;
    private final SSLContext ssl;

    private ClientManager webSocketClient = null;
    protected Session session = null;
    protected volatile boolean closed = true;

    public WebSocketClient(SSLContext ssl, String hostname, int httpsPort, int wssPort, boolean doLogin,
            String username, String password, boolean hostVerificationEnabled, int bufferSize) {
        this.ssl = ssl;
        this.hostname = hostname;
        this.httpsPort = httpsPort;
        this.wssPort = wssPort;
        this.doLogin = doLogin;
        this.username = username;
        this.password = password;
        this.hostVerificationEnabled = hostVerificationEnabled;
        this.bufferSize = bufferSize;

        Preconditions.checkNotNull(hostname, "%s must be supplied", "host name");
        Preconditions.checkNotNull(httpsPort, "%s must be supplied", "HTTPS port");
        Preconditions.checkNotNull(wssPort, "%s must be supplied", "WSS port");

        if (doLogin
                && ((StringUtils.isEmpty(username) && !StringUtils.isEmpty(password) || (!StringUtils.isEmpty(username) && StringUtils
                        .isEmpty(password))))) {
            throw new IllegalArgumentException("Both username and password must be empty or non-empty");
        }

    }

    public WebSocketClient(String hostname, int httpsPort, int wssPort, boolean doLogin, String username,
            String password, String keyStoreFile, String keyStoreType, String keyStorePass, String trustStoreFile,
            String trustStoreType, String trustStorePass, boolean hostVerificationEnabled, int bufferSize) {
        this(HttpClient.getSSLContext(trustStoreFile, trustStoreType, trustStorePass, keyStoreFile, keyStoreType,
                keyStorePass), hostname, httpsPort, wssPort, doLogin, username, password, hostVerificationEnabled,
                bufferSize);
    }

    public void open(ClientHandler clientEndpoint) throws IOException, DeploymentException, URISyntaxException {

        Cookie sessionCookie = null;
        if (doLogin) {
            BasicCookieStore cookieJar = new BasicCookieStore();
            try (CloseableHttpClient client = HttpClient.get(ssl, cookieJar, hostVerificationEnabled)) {

                String target = "https://" + hostname + ":" + httpsPort + "/login";

                HttpRequestBase request = null;
                if (StringUtils.isEmpty(username)) {
                    // HTTP GET to /login to use certificate based login
                    request = new HttpGet(target);
                    LOG.trace("Performing client certificate login");
                } else {
                    // HTTP POST to /login to use username/password
                    BasicAuthLogin login = new BasicAuthLogin();
                    login.setUsername(username);
                    login.setPassword(password);
                    byte[] payload = JsonSerializer.getObjectMapper().writeValueAsBytes(login);
                    HttpPost post = new HttpPost(target);
                    post.setEntity(new ByteArrayEntity(payload));
                    request = post;
                    LOG.trace("Performing BasicAuth login");
                }

                HttpResponse response = null;
                try {
                    response = client.execute(request);
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        throw new HttpResponseException(response.getStatusLine().getStatusCode(), response
                                .getStatusLine().getReasonPhrase());
                    }
                    for (Cookie c : cookieJar.getCookies()) {
                        if (c.getName().equals("QSESSIONID")) {
                            sessionCookie = c;
                            break;
                        }
                    }
                    if (null == sessionCookie) {
                        throw new IllegalStateException("Unable to find session id cookie header in login response");
                    }
                } finally {
                    HttpClientUtils.closeQuietly(response);
                }
            }
        }

        SslEngineConfigurator sslEngine = new SslEngineConfigurator(ssl);
        sslEngine.setClientMode(true);
        sslEngine.setHostVerificationEnabled(hostVerificationEnabled);

        webSocketClient = ClientManager.createClient();
        webSocketClient.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngine);
        webSocketClient.getProperties().put(ClientProperties.INCOMING_BUFFER_SIZE, bufferSize);
        String wssPath = "wss://" + hostname + ":" + wssPort + "/websocket";
        session = webSocketClient.connectToServer(clientEndpoint, new QEndpointConfig(sessionCookie), new URI(wssPath));

        final ByteBuffer pingData = ByteBuffer.allocate(0);
        webSocketClient.getScheduledExecutorService().scheduleAtFixedRate(() -> {
            try {
                session.getBasicRemote().sendPing(pingData);
            } catch (Exception e) {
                LOG.error("Error sending ping", e);
            }
        }, 30, 60, TimeUnit.SECONDS);
        closed = false;
    }

    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        try {
            if (null != webSocketClient) {
                webSocketClient.shutdown();
            }
        } finally {
            session = null;
            webSocketClient = null;
            closed = true;
        }
    }

    public void sendRequest(Object r) throws JsonProcessingException, IOException {
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(JsonSerializer.getObjectMapper().writeValueAsBytes(r)));
    }

    public boolean isClosed() {
        return closed;
    }

}
