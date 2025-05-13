package qonduit.operations.splits;

import qonduit.operation.OperationResponse;

public class SplitLookupResponse extends OperationResponse {

    private String beginRow;
    private String endRow;

    public SplitLookupResponse() {
        super();
    }

    public String getBeginRow() {
        return beginRow;
    }

    public void setBeginRow(String beginRow) {
        this.beginRow = beginRow;
    }

    public String getEndRow() {
        return endRow;
    }

    public void setEndRow(String endRow) {
        this.endRow = endRow;
    }

}
