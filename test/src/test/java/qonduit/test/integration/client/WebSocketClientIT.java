package qonduit.test.integration.client;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.JdkSslClientContext;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.BatchWriterConfig;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.http.client.HttpResponseException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.Server;
import qonduit.api.request.WebSocketRequest;
import qonduit.auth.AuthCache;
import qonduit.client.websocket.ClientHandler;
import qonduit.client.websocket.WebSocketClient;
import qonduit.operations.scanner.KVPair;
import qonduit.operations.scanner.ScanRequest;
import qonduit.operations.scanner.Value;
import qonduit.operations.version.VersionRequest;
import qonduit.operations.version.VersionResponse;
import qonduit.serialize.JsonSerializer;
import qonduit.test.IntegrationTest;
import qonduit.test.integration.OneWaySSLBase;

@SuppressWarnings("deprecation")
@Category(IntegrationTest.class)
public class WebSocketClientIT extends OneWaySSLBase {

    private final static Logger LOG = LoggerFactory.getLogger(WebSocketClientIT.class);

    private static SSLContext sslCtx = null;
    private Server s = null;

    private void setupSslCtx() throws Exception {
        Assert.assertNotNull(clientTrustStoreFile);
        SslContextBuilder builder = SslContextBuilder.forClient();
        builder.applicationProtocolConfig(ApplicationProtocolConfig.DISABLED);
        builder.sslProvider(SslProvider.JDK);
        builder.trustManager(clientTrustStoreFile); // Trust the server cert
        SslContext ctx = builder.build();
        Assert.assertEquals(JdkSslClientContext.class, ctx.getClass());
        JdkSslContext jdk = (JdkSslContext) ctx;
        sslCtx = jdk.context();
    }

    @Before
    public void setup() throws Exception {
        Connector con = mac.getConnector("root", "secret");
        con.securityOperations().changeUserAuthorizations("root", new Authorizations("A", "B", "C", "D", "E", "F"));
        s = new Server(conf);
        s.run();
        setupSslCtx();
        clearTablesResetConf();
    }

    @After
    public void tearDown() throws Exception {
        s.shutdown();
        AuthCache.resetSessionMaxAge();
    }

    public void doIt(WebSocketClient client, WebSocketRequest request, List<byte[]> responses, int wait)
            throws Exception {

        ClientHandler handler = new ClientHandler() {

            @Override
            public void onOpen(Session session, EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<byte[]>() {

                    @Override
                    public void onMessage(byte[] message) {
                        responses.add(message);
                        LOG.debug("Message received on Websocket session {}: {}", session.getId(), new String(message));
                    }
                });
            }

            @Override
            public void onClose(Session session, CloseReason reason) {
                super.onClose(session, reason);
                try {
                    client.close();
                } catch (IOException e) {
                    Assert.fail("Error calling close on client: " + e.getMessage());
                }
            }

            @Override
            public void onError(Session session, Throwable error) {
                super.onError(session, error);
                try {
                    client.close();
                } catch (IOException e) {
                    Assert.fail("Error calling close on client: " + e.getMessage());
                }
            }
        };

        try {
            client.open(handler);
            client.sendRequest(request);
            sleepUninterruptibly(wait, TimeUnit.SECONDS);
        } finally {
            client.close();
        }
    }

    private void doVersion(WebSocketClient client) throws Exception {
        List<byte[]> responses = new ArrayList<>();
        String id = UUID.randomUUID().toString();
        VersionRequest request = new VersionRequest();
        request.setRequestId(id);
        doIt(client, request, responses, 1);
        Assert.assertEquals(1, responses.size());
        VersionResponse response = JsonSerializer.getObjectMapper().readValue(responses.get(0), VersionResponse.class);
        Assert.assertEquals(VersionResponse.VERSION, response.getVersion());
        Assert.assertEquals(id, response.getRequestId());
    }

    @Test
    public void testClientAnonymousAccess() throws Exception {
        WebSocketClient client = new WebSocketClient(sslCtx, "localhost", 54322, 54323, false, null, null, false, 65536);
        doVersion(client);
    }

    @Test
    public void testClientBasicAuthAccess() throws Exception {
        WebSocketClient client = new WebSocketClient(sslCtx, "localhost", 54322, 54323, true, "test", "test1", false,
                65536);
        doVersion(client);
    }

    @Test(expected = HttpResponseException.class)
    public void testClientBasicAuthAccessFailure() throws Exception {
        WebSocketClient client = new WebSocketClient(sslCtx, "localhost", 54322, 54323, true, "test", "test2", false,
                65536);
        doVersion(client);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClientBasicAuthParameterMismatch() throws Exception {
        WebSocketClient client = new WebSocketClient(sslCtx, "localhost", 54322, 54323, true, "test", null, false,
                65536);
        doVersion(client);
    }

    private void doScan(WebSocketClient client) throws Exception {
        long now = System.currentTimeMillis();
        String tableName = "qonduit.scanTest";
        Connector con = mac.getConnector(MAC_ROOT_USER, MAC_ROOT_PASSWORD);
        con.namespaceOperations().create("qonduit");
        con.tableOperations().create(tableName);
        BatchWriterConfig bwc = new BatchWriterConfig();
        bwc.setMaxLatency(2, TimeUnit.SECONDS);
        BatchWriter writer = con.createBatchWriter(tableName, bwc);

        ColumnVisibility cv = new ColumnVisibility();
        for (int i = 0; i < 10; i++) {
            Mutation m = new Mutation("m" + i);
            m.put("cf" + i, "cq" + i, cv, now + i, Integer.toString(i));
            writer.addMutation(m);
        }
        writer.flush();
        writer.close();
        sleepUninterruptibly(2, TimeUnit.SECONDS);
        List<byte[]> responses = new ArrayList<>();
        String id = UUID.randomUUID().toString();
        ScanRequest request = new ScanRequest();
        request.setRequestId(id);
        request.setTableName(tableName);
        request.setResultBatchSize(5);
        doIt(client, request, responses, 3);
        Assert.assertEquals(11, responses.size());
        for (byte[] b : responses) {
            KVPair kv = JsonSerializer.getObjectMapper().readValue(b, KVPair.class);
            Value val = kv.getValue();
            if (null != val) {
                int num = Integer.parseInt(new String(val.getValue()));
                Key key = kv.getKey().toKey();
                Assert.assertEquals("m" + num, key.getRow().toString());
                Assert.assertEquals("cf" + num, key.getColumnFamily().toString());
                Assert.assertEquals("cq" + num, key.getColumnQualifier().toString());
                Assert.assertEquals(now + num, key.getTimestamp());
                Assert.assertEquals(id, kv.getRequestId());
            } else {
                Assert.assertTrue(kv.isEndOfResults());
            }
        }
    }

    @Test
    public void testScan() throws Exception {
        WebSocketClient client = new WebSocketClient(sslCtx, "localhost", 54322, 54323, true, "test", "test1", false,
                65536);
        doScan(client);
    }

}
