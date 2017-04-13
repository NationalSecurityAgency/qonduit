package qonduit.operation;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.security.Authorizations;

import qonduit.api.request.WebSocketRequest;
import qonduit.util.JsonUtil;

public abstract class BatchingOperation<T> implements Operation {

    protected ChannelHandlerContext context = null;
    protected Connector connector = null;
    protected Authorizations auths = null;
    protected WebSocketRequest request = null;
    protected volatile boolean closed = false;
    protected ConcurrentSkipListSet<ByteBuf> batch = new ConcurrentSkipListSet<>();
    private int batchSize = 100;

    @Override
    public void init(ChannelHandlerContext context, Connector connector, Authorizations auths, WebSocketRequest r) {
        this.context = context;
        this.connector = connector;
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
