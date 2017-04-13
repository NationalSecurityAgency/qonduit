package qonduit.operations.scanner;

import qonduit.utils.ByteArrayUtils;

public class Key {

    protected byte[] row;
    protected byte[] colFamily;
    protected byte[] colQualifier;
    protected byte[] colVisibility;
    protected long timestamp;
    protected boolean deleted;

    public Key() {
    }

    public Key(byte[] row, byte[] colFamily, byte[] colQualifier, byte[] colVisibility, long timestamp, boolean deleted) {
        super();
        this.setRow(row);
        this.setColFamily(colFamily);
        this.setColQualifier(colQualifier);
        this.setColVisibility(colVisibility);
        this.timestamp = timestamp;
        this.deleted = deleted;
    }

    public byte[] getRow() {
        return row;
    }

    public byte[] getColFamily() {
        return colFamily;
    }

    public byte[] getColQualifier() {
        return colQualifier;
    }

    public byte[] getColVisibility() {
        return colVisibility;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setRow(byte[] row) {
        this.row = ByteArrayUtils.copy(row);
    }

    public void setColFamily(byte[] colFamily) {
        this.colFamily = ByteArrayUtils.copy(colFamily);
    }

    public void setColQualifier(byte[] colQualifier) {
        this.colQualifier = ByteArrayUtils.copy(colQualifier);
    }

    public void setColVisibility(byte[] colVisibility) {
        this.colVisibility = ByteArrayUtils.copy(colVisibility);
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public static Key fromKey(org.apache.accumulo.core.data.Key k) {
        Key key = new Key();
        key.setRow(k.getRowData().toArray());
        key.setColFamily(k.getColumnFamilyData().toArray());
        key.setColQualifier(k.getColumnQualifierData().toArray());
        key.setColVisibility(k.getColumnVisibilityData().toArray());
        key.timestamp = k.getTimestamp();
        key.deleted = k.isDeleted();
        return key;
    }

    public org.apache.accumulo.core.data.Key toKey() {
        return new org.apache.accumulo.core.data.Key(this.row, this.colFamily, this.colQualifier, this.colVisibility,
                this.timestamp, this.deleted);
    }

}
