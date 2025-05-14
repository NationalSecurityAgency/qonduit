package qonduit.operations.splits;

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

}
