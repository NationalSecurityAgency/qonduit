package qonduit.operation;

import qonduit.api.request.WebSocketRequest;

public class ErrorResponse extends OperationResponse {

    private WebSocketRequest request;

    public WebSocketRequest getRequest() {
        return request;
    }

    public void setRequest(WebSocketRequest request) {
        this.request = request;
    }

}
