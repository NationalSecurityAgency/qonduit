package qonduit.auth;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.accumulo.core.security.Authorizations;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import qonduit.Configuration;
import qonduit.api.response.QonduitException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class AuthCache {

    private static Cache<String, Authentication> CACHE = null;

    private static int sessionMaxAge = -1;

    /**
     * For tests only
     */
    public static void resetSessionMaxAge() {
        sessionMaxAge = -1;
    }

    public static void setSessionMaxAge(Configuration config) {
        if (-1 != sessionMaxAge) {
            throw new IllegalStateException("Cache session max age already configured.");
        }
        sessionMaxAge = config.getSecurity().getSessionMaxAge();
    }

    public static Cache<String, Authentication> getCache() {
        if (-1 == sessionMaxAge) {
            throw new IllegalStateException("Cache session max age not configured.");
        }
        if (null == CACHE) {
            CACHE = Caffeine.newBuilder().expireAfterAccess(sessionMaxAge, TimeUnit.SECONDS).build();
        }
        return CACHE;
    }

    public static Authorizations getAuthorizations(String sessionId) {
        if (!StringUtils.isEmpty(sessionId)) {
            Authentication auth = CACHE.asMap().get(sessionId);
            if (null != auth) {
                Collection<? extends GrantedAuthority> authorities = CACHE.asMap().get(sessionId).getAuthorities();
                String[] auths = new String[authorities.size()];
                final AtomicInteger i = new AtomicInteger(0);
                authorities.forEach(a -> auths[i.getAndIncrement()] = a.getAuthority());
                return new Authorizations(auths);
            } else {
                return null;
            }
        } else {
            throw new IllegalArgumentException("session id cannot be null");
        }
    }

    public static void enforceAccess(Configuration conf, String sessionId) throws Exception {
        if (!conf.getSecurity().isAllowAnonymousAccess()) {
            if (StringUtils.isEmpty(sessionId)) {
                throw new QonduitException(HttpResponseStatus.UNAUTHORIZED.code(), "User must log in",
                        "Anonymous access is disabled, log in first");
            }
            if (!AuthCache.getCache().asMap().containsKey(sessionId)) {
                throw new QonduitException(HttpResponseStatus.UNAUTHORIZED.code(), "User must log in",
                        "Unknown session id was submitted, log in again");
            }
        }
    }

}
