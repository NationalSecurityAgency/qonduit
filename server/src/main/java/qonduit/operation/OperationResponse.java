package qonduit.operation;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class OperationResponse {

    private String requestId = null;
    private String errorMessage = null;
    private boolean endOfResults = false;

    public String getRequestId() {
        return requestId;
    }

    @JsonIgnore
    public boolean isError() {
        return (null != errorMessage);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isEndOfResults() {
        return endOfResults;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setEndOfResults(boolean endOfResults) {
        this.endOfResults = endOfResults;
    }

}
