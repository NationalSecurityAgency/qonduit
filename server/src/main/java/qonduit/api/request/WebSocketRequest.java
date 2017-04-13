package qonduit.api.request;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "operation")
public interface WebSocketRequest extends Request {

    /*
     * WebSocket requests will contain a property named 'operation' as well as
     * this method for mapping request messages to request handlers.
     */
    public String getOperation();

}
