package qonduit.operation;

import io.netty.channel.ChannelHandlerContext;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.security.Authorizations;

import qonduit.api.request.WebSocketRequest;

public interface Operation extends Runnable {

    /**
     * Initialize the operation so that it's ready to run
     * 
     * @param context
     * @param connector
     * @param auths
     */
    public void init(ChannelHandlerContext context, Connector connector, Authorizations auths, WebSocketRequest r);

    /**
     * 
     * @return the request type for this operation
     */
    public WebSocketRequest getRequestType();

    /**
     * 
     * @return name of the class that will contain the response objects
     */
    public Class<?> getResponseClass();

    /**
     * Method used to signal that this operation should stop
     */
    public void close();

}
