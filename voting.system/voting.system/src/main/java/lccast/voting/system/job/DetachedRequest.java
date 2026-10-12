package lccast.voting.system.job;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.Proxy;
import java.util.*;

/** A copy of the request/session values needed after the real request has finished. */
public class DetachedRequest extends HttpServletRequestWrapper {

    private final HttpSession session;
    private final String remoteAddr;
    private final String requestUri;
    private final String method;
    private final Map<String, String> headers;

    private DetachedRequest(HttpServletRequest real, HttpSession session, String remoteAddr,
                            String requestUri, String method, Map<String, String> headers) {
        super(real);
        this.session = session;
        this.remoteAddr = remoteAddr;
        this.requestUri = requestUri;
        this.method = method;
        this.headers = headers;
    }

    public static DetachedRequest from(HttpServletRequest req) {
        HttpSession real = req.getSession(false);
        Map<String, Object> attrs = new HashMap<>();
        String sessionId = null;

        if (real != null) {
            sessionId = real.getId();
            for (String name : Collections.list(real.getAttributeNames())) {
                attrs.put(name, real.getAttribute(name));
            }
        }
        final String sid = sessionId;

        HttpSession snapshot = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class[]{HttpSession.class},
                (proxy, m, args) -> {
                    switch (m.getName()) {
                        case "getAttribute": return attrs.get((String) args[0]);
                        case "getId": return sid;
                        case "getAttributeNames": return Collections.enumeration(attrs.keySet());
                        default:
                            Class<?> r = m.getReturnType();
                            if (r == boolean.class) return false;
                            if (r == int.class) return 0;
                            if (r == long.class) return 0L;
                            return null;
                    }
                });

        Map<String, String> headers = new HashMap<>();
        for (String h : List.of("User-Agent", "X-Forwarded-For", "X-Real-IP")) {
            String v = req.getHeader(h);
            if (v != null) headers.put(h.toLowerCase(), v);
        }

        return new DetachedRequest(req, snapshot, req.getRemoteAddr(),
                req.getRequestURI(), req.getMethod(), headers);
    }

    @Override public HttpSession getSession() { return session; }
    @Override public HttpSession getSession(boolean create) { return session; }
    @Override public String getRemoteAddr() { return remoteAddr; }
    @Override public String getRequestURI() { return requestUri; }
    @Override public String getMethod() { return method; }
    @Override public String getHeader(String name) { return headers.get(name.toLowerCase()); }
}