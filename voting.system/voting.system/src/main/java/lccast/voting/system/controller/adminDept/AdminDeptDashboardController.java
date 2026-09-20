package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.service.SupabaseAuthService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/admin-dept")
public class AdminDeptDashboardController {

    private final SupabaseAuthService supabaseAuthService;
    private final UserProfileRepository userProfileRepository;
    private final SupabaseStorageService storageService;

    public AdminDeptDashboardController(
            SupabaseAuthService supabaseAuthService,
            UserProfileRepository userProfileRepository,
            SupabaseStorageService storageService) {
        this.supabaseAuthService = supabaseAuthService;
        this.userProfileRepository = userProfileRepository;
        this.storageService = storageService;
    }

    @GetMapping("/dashboard")
    public String dashboard(
            HttpSession session,
            Model model) {

        // ... unchanged, exactly as you have it ...
        String firstName = (String) session.getAttribute("firstName");
        String role = (String) session.getAttribute("role");
        String programCourse = (String) session.getAttribute("programCourse");
        String campus = (String) session.getAttribute("campus");

        String authUserIdStr = (String) session.getAttribute("userId");

        String email = null;
        String profilePictureUrl = null;

        if (authUserIdStr != null) {
            UUID authUserId = UUID.fromString(authUserIdStr);
            UserProfile profile = userProfileRepository.findByAuthUserId(authUserId).orElse(null);

            if (profile != null) {
                email = profile.getEmail();
                if (profile.getProfilePictureUrl() != null) {
                    profilePictureUrl = "/files/" + profile.getProfilePictureUrl();
                }
            }
        }

        String displayName;
        if (firstName != null && !firstName.trim().isEmpty()) {
            displayName = firstName;
        } else {
            displayName = role;
        }

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);
        model.addAttribute("programCourse", programCourse);
        model.addAttribute("campus", campus);
        model.addAttribute("profilePictureUrl", profilePictureUrl);

        return "admin-dept/dashboard.html";
    }
}