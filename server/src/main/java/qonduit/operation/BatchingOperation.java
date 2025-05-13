package qonduit.operation;

import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.security.Authorizations;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import qonduit.api.request.WebSocketRequest;
import qonduit.util.JsonUtil;

public abstract class BatchingOperation<T> implements Operation {

    protected ChannelHandlerContext context = null;
    protected AccumuloClient client = null;
    protected Authorizations auths = null;
    protected WebSocketRequest request = null;
    protected volatile boolean closed = false;
    protected ConcurrentSkipListSet<ByteBuf> batch = new ConcurrentSkipListSet<>();
    private volatile int batchSize = 100;

    @Override
    public void init(ChannelHandlerContext context, AccumuloClient client, Authorizations auths, WebSocketRequest r) {
        this.context = context;
        this.client = client;
        this.auths = auths;
        this.request = r;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    protected void add(T response) throws Exception {
        batch.add(Unpooled.wrappedBuffer(JsonUtil.getObjectMapper().writeValueAsBytes(response)));
        if (batch.size() > this.batchSize) {
            flush();
        }
    }

    protected void flush() {
        this.batch.forEach(o -> this.context.writeAndFlush(new BinaryWebSocketFrame(o)));
    }

    @Override
    public void close() {
        this.closed = true;
    }

}
