package qonduit.operations.scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import qonduit.operations.scanner.ScanRequest.Column;
import qonduit.operations.scanner.ScanRequest.Range;
import qonduit.serialize.JsonSerializer;
import qonduit.util.JsonUtil;

public class ScanRequestDeserializationTest {

    @Test
    public void testDeserialization() throws Exception {
        long now = System.currentTimeMillis();
        Range r = new Range();
        r.setStart(new Key("a".getBytes(UTF_8), "b".getBytes(UTF_8), "c".getBytes(UTF_8), "d".getBytes(UTF_8), now,
                false));
        r.setStartKeyInclusive(true);
        r.setStop(new Key("e".getBytes(UTF_8), "f".getBytes(UTF_8), "g".getBytes(UTF_8), "h".getBytes(UTF_8), now, true));
        r.setStopKeyInclusive(true);
        ScanRequest request = new ScanRequest();
        request.setContext("foo");
        request.setRange(r);
        request.setResultBatchSize(1000);
        request.setScannerBatchSize(5000);
        request.setScannerReadAhead(5);
        request.setTableName("t1");
        request.setTimeoutSeconds(30);
        List<byte[]> cf = new ArrayList<>();
        cf.add("x".getBytes(UTF_8));
        cf.add("y".getBytes(UTF_8));
        request.setColumnFamilies(cf);
        Column c1 = new Column("z".getBytes(UTF_8), "z1".getBytes(UTF_8));
        Column c2 = new Column("z".getBytes(UTF_8), "z2".getBytes(UTF_8));
        List<Column> cols = new ArrayList<>();
        cols.add(c1);
        cols.add(c2);
        request.setColumns(cols);

        byte[] b = JsonSerializer.getObjectMapper().writeValueAsBytes(request);

        ScanRequest req = JsonUtil.getObjectMapper().readValue(b, ScanRequest.class);
        Assert.assertEquals(30, req.getTimeoutSeconds());
        Assert.assertEquals("t1", req.getTableName());
        Assert.assertEquals(5, req.getScannerReadAhead());
        Assert.assertEquals(5000, req.getScannerBatchSize());
        Assert.assertEquals(1000, req.getResultBatchSize());
        Assert.assertEquals("foo", req.getContext());
        Assert.assertNotNull(req.getRange());
        Range r2 = req.getRange();
        Assert.assertEquals(true, r2.isStartKeyInclusive());
        Assert.assertEquals(true, r2.isStopKeyInclusive());
        Assert.assertNotNull(r2.getStart());
        Key start = r2.getStart();
        Assert.assertArrayEquals("a".getBytes(UTF_8), start.getRow());
        Assert.assertArrayEquals("b".getBytes(UTF_8), start.getColFamily());
        Assert.assertArrayEquals("c".getBytes(UTF_8), start.getColQualifier());
        Assert.assertArrayEquals("d".getBytes(UTF_8), start.getColVisibility());
        Assert.assertEquals(now, start.getTimestamp());
        Assert.assertEquals(false, start.isDeleted());
        Assert.assertNotNull(r2.getStop());
        Key stop = r2.getStop();
        Assert.assertArrayEquals("e".getBytes(UTF_8), stop.getRow());
        Assert.assertArrayEquals("f".getBytes(UTF_8), stop.getColFamily());
        Assert.assertArrayEquals("g".getBytes(UTF_8), stop.getColQualifier());
        Assert.assertArrayEquals("h".getBytes(UTF_8), stop.getColVisibility());
        Assert.assertEquals(now, stop.getTimestamp());
        Assert.assertEquals(true, stop.isDeleted());
        Assert.assertNotNull(req.getColumnFamilies());
        List<byte[]> cfs = req.getColumnFamilies();
        Assert.assertEquals(2, cfs.size());
        for (byte[] c : cfs) {
            /**
             * Had to check values as Strings here. The byte encoding of the
             * deserialized byte array did not match "x".getBytes()
             */
            if (!("x".equals(new String(c)) || "y".equals(new String(c)))) {
                Assert.fail("Missing value");
            }
        }
        Assert.assertNotNull(req.getColumns());
        List<Column> cc = req.getColumns();
        for (Column c : cc) {
            Assert.assertTrue("z".equals(new String(c.getFamily())));
            if (!("z1".equals(new String(c.getQualifier())) || "z2".equals(new String(c.getQualifier())))) {
                Assert.fail("Missing value");
            }
        }

    }

}
