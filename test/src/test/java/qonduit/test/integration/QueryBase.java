package qonduit.test.integration;

import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public abstract class QueryBase extends MacITBase {

    public static class NotSuccessfulException extends Exception {

        private static final long serialVersionUID = 1L;
    }

    public static class UnauthorizedUserException extends Exception {

        private static final long serialVersionUID = 1L;

        public UnauthorizedUserException() {
        }
    }

    protected abstract HttpsURLConnection getUrlConnection(URL url) throws Exception;

    protected abstract HttpsURLConnection getUrlConnection(String username, String password, URL url) throws Exception;

}
