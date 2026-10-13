package lccast.voting.system.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.security.LandingPageResolver;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AppErrorController implements ErrorController {

    private final LandingPageResolver landingPageResolver;

    public AppErrorController(LandingPageResolver landingPageResolver) {
        this.landingPageResolver = landingPageResolver;
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Authentication auth) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = (status instanceof Integer i) ? i : 500;

        if (code == 400 || code == 403 || code == 404) {
            return "redirect:" + landingPageResolver.resolve(auth);
        }
        return "error"; // real server errors (500) still show an error page; needs templates/error.html
    }
}