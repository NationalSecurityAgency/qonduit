package qonduit.operation;

import qonduit.api.request.WebSocketRequest;

public abstract class OperationRequest implements WebSocketRequest {

    private String requestId;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

}
