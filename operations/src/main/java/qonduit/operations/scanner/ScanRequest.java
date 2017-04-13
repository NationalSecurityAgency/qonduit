package qonduit.operations.scanner;

import java.util.List;

import qonduit.operation.OperationRequest;
import qonduit.utils.ByteArrayUtils;

public class ScanRequest extends OperationRequest {

    public static class Range {

        private Key start;
        private Key stop;
        private boolean startKeyInclusive;
        private boolean stopKeyInclusive;

        public Key getStart() {
            return start;
        }

        public Key getStop() {
            return stop;
        }

        public boolean isStartKeyInclusive() {
            return startKeyInclusive;
        }

        public boolean isStopKeyInclusive() {
            return stopKeyInclusive;
        }

        public void setStart(Key start) {
            this.start = start;
        }

        public void setStop(Key stop) {
            this.stop = stop;
        }

        public void setStartKeyInclusive(boolean startKeyInclusive) {
            this.startKeyInclusive = startKeyInclusive;
        }

        public void setStopKeyInclusive(boolean stopKeyInclusive) {
            this.stopKeyInclusive = stopKeyInclusive;
        }

        public org.apache.accumulo.core.data.Range toRange() {
            return new org.apache.accumulo.core.data.Range(start.toKey(), startKeyInclusive, stop.toKey(),
                    stopKeyInclusive);
        }
    }

    public static class Column {

        private byte[] family = null;
        private byte[] qualifier = null;

        public Column() {
        }

        public Column(byte[] family, byte[] qualifier) {
            super();
            this.setFamily(family);
            this.setQualifier(qualifier);
        }

        public byte[] getFamily() {
            return family;
        }

        public byte[] getQualifier() {
            return qualifier;
        }

        public void setFamily(byte[] family) {
            this.family = ByteArrayUtils.copy(family);
        }

        public void setQualifier(byte[] qualifier) {
            this.qualifier = ByteArrayUtils.copy(qualifier);
        }

    }

    public static final String operation = "scan";

    private String tableName = null;
    private int scannerReadAhead = 1;
    private int scannerBatchSize = 1000;
    private int resultBatchSize = 100;
    private int timeoutSeconds = 0;
    private String context = null;
    private Range range = null;
    private List<byte[]> columnFamilies = null;
    private List<Column> columns = null;

    public String getOperation() {
        return operation;
    }

    public String getTableName() {
        return tableName;
    }

    public int getScannerReadAhead() {
        return scannerReadAhead;
    }

    public int getScannerBatchSize() {
        return scannerBatchSize;
    }

    public int getResultBatchSize() {
        return resultBatchSize;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setScannerReadAhead(int scannerReadAhead) {
        this.scannerReadAhead = scannerReadAhead;
    }

    public void setScannerBatchSize(int scannerBatchSize) {
        this.scannerBatchSize = scannerBatchSize;
    }

    public void setResultBatchSize(int resultBatchSize) {
        this.resultBatchSize = resultBatchSize;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public List<byte[]> getColumnFamilies() {
        return columnFamilies;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public void setColumnFamilies(List<byte[]> columnFamilies) {
        this.columnFamilies = columnFamilies;
    }

    public void setColumns(List<Column> columns) {
        this.columns = columns;
    }

}
