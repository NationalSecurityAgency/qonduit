package qonduit.operation;

import org.apache.accumulo.core.security.Authorizations;

import io.netty.channel.ChannelHandlerContext;
import qonduit.Server;
import qonduit.api.request.WebSocketRequest;

public interface Operation extends Runnable {

    /**
     * Initialize the operation so that it's ready to run
     */
    public void init(ChannelHandlerContext context, Server server, Authorizations auths, WebSocketRequest r);

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
