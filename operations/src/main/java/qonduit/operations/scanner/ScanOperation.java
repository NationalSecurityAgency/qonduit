package qonduit.operations.scanner;

import io.netty.channel.ChannelHandlerContext;

import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import jline.internal.Log;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;

import qonduit.api.request.WebSocketRequest;
import qonduit.operation.BatchingOperation;

public class ScanOperation extends BatchingOperation<KVPair> {

    private static final ScanRequest requestType = new ScanRequest();
    private ScanRequest request = null;

    @Override
    public void init(ChannelHandlerContext context, Connector connector, Authorizations auths, WebSocketRequest r) {
        super.init(context, connector, auths, r);
        if (r instanceof ScanRequest) {
            this.request = (ScanRequest) r;
            this.setBatchSize(this.request.getResultBatchSize());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void run() {
        try {
            Scanner s = this.connector.createScanner(this.request.getTableName(), auths);
            s.setBatchSize(this.request.getScannerBatchSize());
            s.setReadaheadThreshold(this.request.getScannerReadAhead());
            if (0 != this.request.getTimeoutSeconds()) {
                s.setTimeout(this.request.getTimeoutSeconds(), TimeUnit.SECONDS);
            }
            s.enableIsolation();
            if (null != this.request.getContext()) {
                s.setClassLoaderContext(this.request.getContext());
            }
            if (null != this.request.getRange()) {
                s.setRange(this.request.getRange().toRange());
            }
            if (null != this.request.getColumnFamilies()) {
                this.request.getColumnFamilies().forEach(cf -> s.fetchColumnFamily(new Text(cf)));
            }
            if (null != this.request.getColumns()) {
                this.request.getColumns().forEach(
                        c -> s.fetchColumn(new Text(c.getFamily()), new Text(c.getQualifier())));
            }
            if (this.closed) {
                return;
            }
            for (Entry<org.apache.accumulo.core.data.Key, org.apache.accumulo.core.data.Value> e : s) {
                if (this.closed) {
                    return;
                }
                KVPair kv = new KVPair(Key.fromKey(e.getKey()), new Value(e.getValue().get()));
                kv.setRequestId(this.request.getRequestId());
                this.add(kv);
            }
            KVPair kv = new KVPair();
            kv.setRequestId(this.request.getRequestId());
            kv.setEndOfResults(true);
            this.add(kv);
        } catch (Exception e) {
            Log.error("Error during scan operation, request: {}", this.request.getRequestId(), e);
            KVPair kv = new KVPair();
            kv.setRequestId(this.request.getRequestId());
            kv.setErrorMessage(e.getMessage());
            try {
                this.add(kv);
            } catch (Exception e1) {
                Log.error("Error sending error back to client, request: {}", this.request.getRequestId(), e1);
            }
        } finally {
            flush();
        }
    }

    @Override
    public WebSocketRequest getRequestType() {
        return requestType;
    }

    @Override
    public Class<?> getResponseClass() {
        return KVPair.class;
    }

}
