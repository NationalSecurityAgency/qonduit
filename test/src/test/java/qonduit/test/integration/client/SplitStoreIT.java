package qonduit.test.integration.client;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.IntStream;

import javax.net.ssl.SSLContext;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.NamespaceExistsException;
import org.apache.accumulo.core.client.admin.NewTableConfiguration;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.metadata.MetadataTable;
import org.apache.accumulo.core.metadata.RootTable;
import org.apache.accumulo.core.util.Pair;
import org.apache.hadoop.io.Text;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import qonduit.Server;
import qonduit.auth.AuthCache;
import qonduit.client.websocket.WebSocketClient;
import qonduit.operations.splits.SplitLookupOperation;
import qonduit.operations.splits.SplitLookupRequest;
import qonduit.operations.splits.SplitLookupResponse;
import qonduit.serialize.JsonSerializer;
import qonduit.test.IntegrationTest;
import qonduit.test.integration.OneWaySSLBase;

@Category(IntegrationTest.class)
public class SplitStoreIT extends OneWaySSLBase {

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

    private Pair<String, String> doSplitLookup(WebSocketClient client, String tableName, String row,
            boolean errorExpected, String errMsg) throws Exception {
        List<byte[]> responses = new ArrayList<>();
        String id = UUID.randomUUID().toString();
        SplitLookupRequest request = new SplitLookupRequest();
        request.setRequestId(id);
        request.setTableName(tableName);
        request.setRow(row);
        WebSocketClientIT.doIt(client, request, responses, 1);
        Assert.assertEquals(1, responses.size());
        SplitLookupResponse response = JsonSerializer.getObjectMapper().readValue(responses.get(0),
                SplitLookupResponse.class);
        Assert.assertEquals(id, response.getRequestId());
        Assert.assertEquals(response.getErrorMessage(), response.isError(), errorExpected);
        Assert.assertTrue(response.isEndOfResults());
        if (errorExpected) {
            Assert.assertTrue("Expected: " + errMsg + ", recieved: " + response.getErrorMessage(),
                    response.getErrorMessage().equals(errMsg));
            return null;
        } else {
            return new Pair<>(response.getBeginRow(), response.getEndRow());
        }
    }

    @Test
    public void testSplitStore() throws Exception {
        WebSocketClient client = new WebSocketClient(sslCtx, "localhost", 54322, 54323, false, null, null, false,
                65536);
        Assert.assertNull(doSplitLookup(client, RootTable.NAME, "a", true,
                String.format(SplitLookupOperation.INVALID_TABLE_ERROR, RootTable.NAME)));
        Assert.assertNull(doSplitLookup(client, MetadataTable.NAME, "a", true,
                String.format(SplitLookupOperation.INVALID_TABLE_ERROR, MetadataTable.NAME)));
        Assert.assertNull(doSplitLookup(client, "fakeTable", "a", true,
                String.format(SplitLookupOperation.TABLE_NOT_FOUND_ERROR, "fakeTable")));

        final String tableName = "qonduit.splitTest";

        final SortedSet<Text> splits = new TreeSet<>();
        // a-z
        IntStream.rangeClosed(97, 122).forEach(i -> splits.add(new Text(((char) i) + "")));

        final NewTableConfiguration ntc = new NewTableConfiguration();
        ntc.withSplits(splits);

        AccumuloClient accumulo = mac.createAccumuloClient(MAC_ROOT_USER, new PasswordToken(MAC_ROOT_PASSWORD));
        try {
            accumulo.namespaceOperations().create("qonduit");
        } catch (NamespaceExistsException e) {
        }
        accumulo.tableOperations().create(tableName, ntc);

        Assert.assertNull(doSplitLookup(client, tableName, "a", true,
                String.format(SplitLookupOperation.NO_SPLITS_FILE_ERROR, tableName)));

        Thread.sleep(10000);

        Pair<String, String> results = doSplitLookup(client, tableName, "_a", false, null);
        Assert.assertNotNull(results);
        Assert.assertEquals("null", results.getFirst());
        Assert.assertEquals("a", results.getSecond());

        results = doSplitLookup(client, tableName, "a", false, null);
        Assert.assertNotNull(results);
        Assert.assertEquals("null", results.getFirst());
        Assert.assertEquals("a", results.getSecond());

        results = doSplitLookup(client, tableName, "a_", false, null);
        Assert.assertNotNull(results);
        Assert.assertEquals("a", results.getFirst());
        Assert.assertEquals("b", results.getSecond());

        accumulo.tableOperations().delete(tableName);
        Assert.assertNull(doSplitLookup(client, tableName, "a", true,
                String.format(SplitLookupOperation.TABLE_NOT_FOUND_ERROR, tableName)));

    }

}
