package lccast.voting.system.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.util.SessionUserUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

@Component
public class ProfilePictureInterceptor implements HandlerInterceptor {

    private final UserProfileRepository userProfileRepository;

    public ProfilePictureInterceptor(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView mav) {

        // Only HTML pages (JSON/@ResponseBody and redirects have no view to fill)
        if (mav == null || mav.getViewName() == null
                || mav.getViewName().startsWith("redirect:")) {
            return;
        }

        // A controller that already set it wins (e.g. admin settings pages)
        if (mav.getModel().get("profilePictureUrl") != null) return;

        UUID authUserId = SessionUserUtil.getAuthUserId(request.getSession(false));
        if (authUserId == null) return;

        userProfileRepository.findByAuthUserId(authUserId)
                .map(UserProfile::getProfilePictureUrl)
                .filter(path -> !path.isBlank())
                .ifPresent(path -> mav.addObject("profilePictureUrl", "/files/" + path));
    }
}