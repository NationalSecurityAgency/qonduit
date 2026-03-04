package qonduit.operations.splits;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.rfile.RFile;
import org.apache.accumulo.core.clientImpl.Namespace;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.TableId;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.metadata.schema.MetadataSchema;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.TabletColumnFamily;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import qonduit.Server;
import qonduit.api.request.WebSocketRequest;
import qonduit.operation.Operation;
import qonduit.store.SplitStore;
import qonduit.util.JsonUtil;

public class SplitLookupOperation implements Operation {

    public static final String SPLIT_SERVER_DISABLED_ERROR = "Split server is disabled";
    public static final String INVALID_TABLE_ERROR = "SplitStore does not keep splits for table: %s";
    public static final String TABLE_NOT_FOUND_ERROR = "Table does not exist: %s";
    public static final String NO_SPLITS_FILE_ERROR = "Splits file not created yet for table: %s";
    public static final String SPLIT_FILE_CREATION_ERROR = "Error creating splits file for table: %s";
    public static final String TABLE_DELETED_ERROR = "Table deleted: %s";

    private static final Logger LOG = LoggerFactory.getLogger(SplitLookupOperation.class);

    private SplitLookupRequest request = null;
    private ChannelHandlerContext ctx = null;
    private Server server = null;

    @Override
    public void run() {

        final SplitLookupResponse response = new SplitLookupResponse();
        response.setRequestId(this.request.getRequestId());
        response.setEndOfResults(true);

        if (server.getSplitStore() == null) {
            response.setErrorMessage(SPLIT_SERVER_DISABLED_ERROR);
        } else {

            final String tableName = request.getTableName();
            final String tableId = server.getDataStore().getConnector().tableOperations().tableIdMap().get(tableName);

            if (tableName.startsWith(Namespace.ACCUMULO.name() + ".")) {
                response.setErrorMessage(String.format(INVALID_TABLE_ERROR, tableName));
            } else if (tableId == null) {
                response.setErrorMessage(String.format(TABLE_NOT_FOUND_ERROR, tableName));
            } else {
                final String lookup = request.getRow();

                AtomicReference<Path> splitsFile = server.getSplitStore().getSplitsFileForTable(tableName);

                if (splitsFile == null) {
                    response.setErrorMessage(String.format(NO_SPLITS_FILE_ERROR, tableName));
                } else if (splitsFile == SplitStore.ERROR_REF) {
                    response.setErrorMessage(String.format(SPLIT_FILE_CREATION_ERROR, tableName));
                } else if (splitsFile == SplitStore.TABLE_DELETED_REF) {
                    response.setErrorMessage(String.format(TABLE_DELETED_ERROR, tableName));
                } else {
                    // do lookup
                    Text lookupRow = MetadataSchema.TabletsSection.encodeRow(TableId.of(tableId), new Text(lookup));

                    Scanner scanner = RFile.newScanner().from(splitsFile.get().toFile().getAbsolutePath())
                            .withoutSystemIterators().withDataCache(1024).withIndexCache(1024).build();

                    scanner.setRange(new Range(lookupRow, null));
                    var iter = scanner.iterator();
                    if (iter.hasNext()) {
                        Map.Entry<Key, Value> entry = iter.next();
                        Text endRow = MetadataSchema.TabletsSection.decodeRow(entry.getKey().getRow()).getSecond();
                        Text prevRow = TabletColumnFamily.decodePrevEndRow(entry.getValue());
                        response.setBeginRow(prevRow == null ? "null" : prevRow.toString());
                        response.setEndRow(endRow == null ? "null" : endRow.toString());
                    }
                }
            }
        }

        try {
            ByteBuf buf = Unpooled.wrappedBuffer(JsonUtil.getObjectMapper().writeValueAsBytes(response));
            this.ctx.writeAndFlush(new BinaryWebSocketFrame(buf));
        } catch (JsonProcessingException e) {
            LOG.error("Error serializing response to send back to client", e);
        }
    }

    @Override
    public void init(ChannelHandlerContext context, Server server, Authorizations auths, WebSocketRequest r) {
        if (r instanceof SplitLookupRequest) {
            this.request = (SplitLookupRequest) r;
            this.server = server;
        } else {
            throw new UnsupportedOperationException();
        }
        this.ctx = context;
    }

    @Override
    public WebSocketRequest getRequestType() {
        return new SplitLookupRequest();
    }

    @Override
    public Class<?> getResponseClass() {
        return SplitLookupResponse.class;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

}
