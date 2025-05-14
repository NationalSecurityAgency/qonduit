package qonduit.test.integration.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import javax.net.ssl.SSLContext;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.NamespaceExistsException;
import org.apache.accumulo.core.client.admin.NewTableConfiguration;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.hadoop.io.Text;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import qonduit.Server;
import qonduit.auth.AuthCache;
import qonduit.client.websocket.ClientHandler;
import qonduit.client.websocket.WebSocketClient;
import qonduit.operations.splits.SplitLookupRequest;
import qonduit.operations.splits.SplitLookupResponse;
import qonduit.serialize.JsonSerializer;
import qonduit.test.IntegrationTest;
import qonduit.test.integration.OneWaySSLBase;

@Category(IntegrationTest.class)
public class SplitClientExampleIT extends OneWaySSLBase {

    private static final Logger LOG = LoggerFactory.getLogger(SplitClientExampleIT.class);

    private SSLContext sslCtx = null;
    private Server s = null;

    private void setupSslCtx() throws Exception {
        Assert.assertNotNull(clientTrustStoreFile);
        SslContextBuilder builder = SslContextBuilder.forClient();
        builder.applicationProtocolConfig(ApplicationProtocolConfig.DISABLED);
        builder.sslProvider(SslProvider.JDK);
        builder.trustManager(clientTrustStoreFile); // Trust the server cert
        SslContext ctx = builder.build();
        Assert.assertTrue(ctx.isClient());
        JdkSslContext jdk = (JdkSslContext) ctx;
        sslCtx = jdk.context();
    }

    @Before
    public void setup() throws Exception {
        conf.getAccumulo().getSplitServer().setEnabled(true);
        conf.getAccumulo().getSplitServer().setRefreshIntervalSeconds(5);
        s = new Server(conf);
        s.run();
        setupSslCtx();
        clearTablesResetConf();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        s.shutdown();
        clearTablesResetConf();
        AuthCache.resetSessionMaxAge();
    }

    private Text charsToText(int... values) {
        StringBuffer buf = new StringBuffer();
        for (int i : values) {
            buf.append(Character.toString(i));
        }
        return new Text(buf.toString());
    }

    @Test
    public void testSplitLookup() throws Exception {

        final String tableName = "qonduit.splitClientTest";
        final SortedSet<Text> expectedSplits = new TreeSet<>();
        // create a-z, aa-zz, aaa-zzz, aaaa-zzzz splits
        IntStream.rangeClosed(97, 122).forEach(i -> expectedSplits.add(charsToText(i)));
        IntStream.rangeClosed(97, 122).forEach(i -> expectedSplits.add(charsToText(i, i)));
        IntStream.rangeClosed(97, 122).forEach(i -> expectedSplits.add(charsToText(i, i, i)));
        IntStream.rangeClosed(97, 122).forEach(i -> expectedSplits.add(charsToText(i, i, i, i)));

        final NewTableConfiguration ntc = new NewTableConfiguration();
        ntc.withSplits(expectedSplits);
        try (AccumuloClient accumulo = mac.createAccumuloClient(MAC_ROOT_USER, new PasswordToken(MAC_ROOT_PASSWORD))) {
            try {
                accumulo.namespaceOperations().create("qonduit");
            } catch (NamespaceExistsException e) {
            }
            accumulo.tableOperations().create(tableName, ntc);
            Collection<Text> actualSplits = accumulo.tableOperations().listSplits(tableName);
            Assert.assertEquals(expectedSplits, new TreeSet<Text>(actualSplits));
        }

        // Table with splits created, Qonduit server is up with Split operation enabled
        // and a 5 second refresh.

        // A websocket is an asynchronous bi-directional transport. Create a connection
        // to the Qonduit server.
        WebSocketClient client = new WebSocketClient(sslCtx, "localhost", 54322, 54323, false, null, null, false,
                1048576);

        // Create a ClientHandler that will be used by the WebSocketClient to handle
        // responses
        // coming from the Qonduit server.
        final ConcurrentHashMap<String, SplitLookupRequest> requests = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, SplitLookupResponse> responses = new ConcurrentHashMap<>();
        final AtomicBoolean responseFailureOccurred = new AtomicBoolean(false);
        final AtomicBoolean websocketFailureOccurred = new AtomicBoolean(false);
        ClientHandler handler = new ClientHandler() {

            @Override
            public void onOpen(Session session, EndpointConfig config) {
                LOG.info("Websocket session {} opened.", session.getId());
                session.addMessageHandler(new MessageHandler.Whole<byte[]>() {

                    @Override
                    public void onMessage(byte[] message) {
                        try {
                            SplitLookupResponse response = JsonSerializer.getObjectMapper().readValue(message,
                                    SplitLookupResponse.class);
                            var oldVal = responses.putIfAbsent(response.getRequestId(), response);
                            if (oldVal != null) {
                                LOG.error("Received duplicate response for row {}",
                                        requests.get(response.getRequestId()));
                                responseFailureOccurred.set(true);
                            }
                        } catch (IOException e) {
                            LOG.error("Error deserializing response", e);
                            responseFailureOccurred.set(true);
                        }
                        LOG.info("Message received on Websocket session {}: {}", session.getId(), message);
                    }
                });
            }

            @Override
            public void onClose(Session session, CloseReason reason) {
                LOG.info("Websocket session {} closed.", session.getId());
            }

            @Override
            public void onError(Session session, Throwable error) {
                LOG.error("Error occurred on Websocket session" + session.getId(), error);
                websocketFailureOccurred.set(true);
            }

        };

        List<Text> randomSplits = new ArrayList<>(expectedSplits);
        Collections.shuffle(randomSplits);
        Assert.assertEquals(expectedSplits.size(), randomSplits.size());

        // Build the requests
        for (Text randomSplit : randomSplits) {
            String requestId = UUID.randomUUID().toString();
            SplitLookupRequest request = new SplitLookupRequest();
            request.setRequestId(requestId);
            request.setTableName(tableName);
            request.setRow(randomSplit.toString());
            requests.put(requestId, request);
        }
        Assert.assertEquals(randomSplits.size(), requests.size());

        // Let's give the server time to create the splits file
        Thread.sleep(10_000);

        client.open(handler); // this is synchronous
        for (SplitLookupRequest r : requests.values()) {
            client.sendRequest(r);
        }
        LOG.info("Sent all requests");

        while (!websocketFailureOccurred.get() && !responseFailureOccurred.get()
                && responses.size() != requests.size()) {
            Thread.sleep(1000);
        }
        Assert.assertFalse(websocketFailureOccurred.get());
        Assert.assertFalse(responseFailureOccurred.get());

        // Close the websocket connection
        client.close();

        // Process the results
        Iterator<Entry<String, SplitLookupResponse>> iter = responses.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, SplitLookupResponse> e = iter.next();
            iter.remove();
            String requestId = e.getKey();
            SplitLookupResponse res = e.getValue();
            Assert.assertFalse(res.getErrorMessage(), res.isError());
            SplitLookupRequest req = requests.remove(requestId);
            Assert.assertNotNull(req);
            LOG.info("Split for row {} is {}->{}", req.getRow(), res.getBeginRow(), res.getEndRow());
        }
        Assert.assertEquals(0, requests.size());
        Assert.assertEquals(0, responses.size());

    }

}
