package qonduit.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.rfile.RFile;
import org.apache.accumulo.core.clientImpl.Namespace;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.TableId;
import org.apache.accumulo.core.dataImpl.KeyExtent;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.TabletColumnFamily;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qonduit.Configuration;

public class SplitStore {

    private class RefreshTask implements Runnable {

        private final AccumuloClient client;

        public RefreshTask(AccumuloClient client) {
            this.client = client;
        }

        @Override
        public void run() {
            for (String tableName : client.tableOperations().list()) {
                writeSplitsForTable(client, client.tableOperations().tableIdMap(), tableName);
            }
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(SplitStore.class);

    private static final ConcurrentHashMap<String, AtomicReference<Path>> SPLIT_FILES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);
    private static final AtomicLong FILE_COUNTER = new AtomicLong(1);
    private static final Map<String, String> FILE_PROPS = Map.of(Property.TABLE_FILE_COMPRESSED_BLOCK_SIZE.getKey(),
            "8K", Property.TABLE_FILE_COMPRESSED_BLOCK_SIZE_INDEX.getKey(), "100K");

    public static final AtomicReference<Path> ERROR_REF = new AtomicReference<>();
    public static final AtomicReference<Path> TABLE_DELETED_REF = new AtomicReference<>();

    private final int refreshIntervalSeconds;
    private final Path directory;
    private final AccumuloClient client;
    private ScheduledFuture<?> task = null;

    public SplitStore(Configuration conf, AccumuloClient client) {
        this.client = client;
        this.directory = Path.of(conf.getAccumulo().getSplitServer().getLocalSplitStorageDirectory());
        this.refreshIntervalSeconds = conf.getAccumulo().getSplitServer().getRefreshIntervalSeconds();
    }

    public AtomicReference<Path> getSplitsFileForTable(String tableName) {
        return SPLIT_FILES.get(tableName);
    }

    private void deleteExistingFiles() throws IOException {
        List<Path> existingFiles = Files.list(directory).collect(Collectors.toList());
        for (Path p : existingFiles) {
            LOG.info("Found existing file at startup, deleting {}", p);
            Files.delete(p);
        }
    }

    public void initialize() throws IOException {
        if (Files.notExists(directory)) {
            Files.createDirectories(directory);
        } else if (!Files.isDirectory(directory)) {
            throw new IOException(directory + " exists but is not a directory");
        } else {
            deleteExistingFiles();
        }
        task = EXECUTOR.scheduleWithFixedDelay(new RefreshTask(client), refreshIntervalSeconds, refreshIntervalSeconds,
                TimeUnit.SECONDS);
        LOG.info("SplitStore initialized");
    }

    public void writeSplitsForTable(AccumuloClient client, Map<String, String> tableMap, String tableName) {

        if (tableName.startsWith(Namespace.ACCUMULO.name() + ".")) {
            return;
        }

        LOG.debug("Getting splits for table: {}", tableName);
        String tid = tableMap.get(tableName);
        if (tid == null) {
            // maybe a new table, try again
            tableMap = client.tableOperations().tableIdMap();
            tid = tableMap.get(tableName);
            if (tid == null) {
                // table deleted
                LOG.info("{} is not in table map, maybe deleted?", tableName);
                SPLIT_FILES.put(tableName, TABLE_DELETED_REF);
                return;
            }
        }
        final TableId tableId = TableId.of(tid);
        try {
            SortedSet<Text> splits = new TreeSet<>(client.tableOperations().listSplits(tableName));
            Path tableSplitsFile = directory
                    .resolve(tableName.replaceAll("\\.", "_") + "_splits_" + FILE_COUNTER.getAndIncrement() + ".rf");
            LOG.debug("Writing splits for {} to {}", tableName, tableSplitsFile);
            try (var writer = RFile.newWriter().to(tableSplitsFile.toString()).withTableProperties(FILE_PROPS)
                    .build()) {
                Text prev = null;
                for (Text endRow : splits) {
                    var extent = new KeyExtent(tableId, endRow, prev);
                    var row = extent.toMetaRow();
                    var key = new Key(row);
                    key.setTimestamp(0);
                    var encodedPrev = TabletColumnFamily.encodePrevEndRow(extent.prevEndRow());
                    writer.append(key, encodedPrev);
                    prev = endRow;
                }
                var extent = new KeyExtent(tableId, null, prev);
                var row = extent.toMetaRow();
                var key = new Key(row);
                key.setTimestamp(0);
                var encodedPrev = TabletColumnFamily.encodePrevEndRow(extent.prevEndRow());
                writer.append(key, encodedPrev);
            }
            LOG.debug("Splits file {} complete for {}", tableSplitsFile, tableName);
            SPLIT_FILES.computeIfAbsent(tableName, (t) -> new AtomicReference<Path>()).set(tableSplitsFile);
        } catch (IOException | AccumuloSecurityException | AccumuloException e) {
            LOG.error("Error getting / writing splits for table: " + tableName, e);
            SPLIT_FILES.put(tableName, ERROR_REF);
        } catch (TableNotFoundException e) {
            // table deleted
            SPLIT_FILES.put(tableName, TABLE_DELETED_REF);
        }
    }

    public void close() throws IOException {
        task.cancel(true);
        EXECUTOR.shutdownNow();
        deleteExistingFiles();
    }

}
