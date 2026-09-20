package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.repository.UserProfileRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/admin-ssc")
public class AdminAnalyticsController {

    private final UserProfileRepository userProfileRepository;

    public AdminAnalyticsController(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @GetMapping("/analytics")
    public String analytics(HttpSession session, Model model) {

        String firstName = (String) session.getAttribute("firstName");
        String role = (String) session.getAttribute("role");
        String programCourse = (String) session.getAttribute("programCourse");
        String campus = (String) session.getAttribute("campus");
        Object campusIdObj = session.getAttribute("campusId");
        String authUserIdStr = (String) session.getAttribute("userId");

        String displayName = (firstName != null && !firstName.trim().isEmpty()) ? firstName : role;

        String profilePictureUrl = null;

        if (authUserIdStr != null) {
            UUID authUserId = UUID.fromString(authUserIdStr);
            UserProfile profile = userProfileRepository.findByAuthUserId(authUserId).orElse(null);

            if (profile != null && profile.getProfilePictureUrl() != null) {
                profilePictureUrl = "/files/" + profile.getProfilePictureUrl();
            }
        }

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);
        model.addAttribute("programCourse", programCourse);
        model.addAttribute("campus", campus);
        model.addAttribute("campusId", campusIdObj != null ? campusIdObj.toString() : "");
        model.addAttribute("profilePictureUrl", profilePictureUrl);

        return "admin-ssc/analytics.html";
    }
}