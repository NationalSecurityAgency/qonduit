package qonduit.api.request.auth;

import qonduit.api.BasicAuthLogin;
import qonduit.api.request.Request;
import qonduit.util.JsonUtil;

public class BasicAuthLoginRequest extends BasicAuthLogin implements Request {

    @Override
    public void validate() {
        Request.super.validate();
        if (username == null || password == null) {
            throw new IllegalArgumentException("Username and password are required.");
        }
    }

    @Override
    public String toString() {
        return "Username: " + username;
    }

    public static BasicAuthLoginRequest parseBody(byte[] content) throws Exception {
        if (null == content) {
            return new BasicAuthLoginRequest();
        }
        return JsonUtil.getObjectMapper().readValue(content, BasicAuthLoginRequest.class);
    }

}
