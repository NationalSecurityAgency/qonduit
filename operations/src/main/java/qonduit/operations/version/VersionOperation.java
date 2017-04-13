package qonduit.operations.version;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.security.Authorizations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.api.request.WebSocketRequest;
import qonduit.operation.Operation;
import qonduit.serialize.JsonSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VersionOperation implements Operation {

    private static final Logger LOG = LoggerFactory.getLogger(VersionOperation.class);
    private static final ObjectMapper om = JsonSerializer.getObjectMapper();

    private VersionRequest request = null;
    private ChannelHandlerContext ctx = null;

    @Override
    public void init(ChannelHandlerContext context, Connector connector, Authorizations auths, WebSocketRequest r) {
        if (r instanceof VersionRequest) {
            this.request = (VersionRequest) r;
        } else {
            throw new UnsupportedOperationException();
        }
        this.ctx = context;
    }

    @Override
    public void run() {
        try {
            VersionResponse response = new VersionResponse();
            response.setRequestId(this.request.getRequestId());
            ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(om.writeValueAsBytes(response))));
        } catch (JsonProcessingException e) {
            LOG.error("Error serializing version response", e);
        }
    }

    @Override
    public WebSocketRequest getRequestType() {
        return new VersionRequest();
    }

    @Override
    public Class<?> getResponseClass() {
        return VersionResponse.class;
    }

    @Override
    public void close() {
    }

}
