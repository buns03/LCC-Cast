package lccast.voting.system.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class MustChangePasswordInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        Object flag = request.getSession().getAttribute("mustChangePassword");
        if (Boolean.TRUE.equals(flag)) {
            String uri = request.getRequestURI();
            if (!uri.equals("/voter/password-handler")
                    && !uri.equals("/voter/change-password")
                    && !uri.startsWith("/css")
                    && !uri.startsWith("/js")
                    && !uri.equals("/logout")) {
                response.sendRedirect("/voter/password-handler");
                return false;
            }
        }
        return true;
    }
}