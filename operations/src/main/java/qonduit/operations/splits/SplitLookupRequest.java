package qonduit.operations.splits;

import com.google.common.base.Preconditions;

import qonduit.operation.OperationRequest;

public class SplitLookupRequest extends OperationRequest {

    public static final String operation = "split-lookup";

    private String tableName;
    private String row;

    public SplitLookupRequest() {
        super();
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRow() {
        return row;
    }

    public void setRow(String row) {
        this.row = row;
    }

    @Override
    public String getOperation() {
        return operation;
    }

    @Override
    public void validate() {
        Preconditions.checkArgument(tableName != null, "tableName is null");
        Preconditions.checkArgument(row != null, "row is null");
    }

}
