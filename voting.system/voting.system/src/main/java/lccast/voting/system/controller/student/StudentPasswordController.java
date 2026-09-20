package lccast.voting.system.controller.student;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.service.SupabaseAuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/voter")
public class StudentPasswordController {

    private final SupabaseAuthService supabaseAuthService;
    private final UserProfileRepository userProfileRepository;

    public StudentPasswordController(
            SupabaseAuthService supabaseAuthService,
            UserProfileRepository userProfileRepository) {

        this.supabaseAuthService = supabaseAuthService;
        this.userProfileRepository = userProfileRepository;
    }

    @GetMapping("/password-handler")
    public String passwordHandler(
            HttpSession session,
            Model model) {

        String firstName =
                (String) session.getAttribute("firstName");

        String role =
                (String) session.getAttribute("role");

        String displayName =
                firstName != null &&
                        !firstName.trim().isEmpty()
                        ? firstName
                        : role;

        model.addAttribute(
                "displayName",
                displayName
        );

        model.addAttribute(
                "role",
                role
        );

        return "voter/password-handler";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session,
            Model model) {

        String userId =
                (String) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        if (password == null || password.isBlank() ||
                confirmPassword == null || confirmPassword.isBlank()) {

            model.addAttribute(
                    "passwordError",
                    "Please fill out both fields."
            );

            return "voter/password-handler";
        }

        if (!password.equals(confirmPassword)) {

            model.addAttribute(
                    "passwordError",
                    "Passwords do not match."
            );

            return "voter/password-handler";
        }

        try {

            supabaseAuthService.updatePassword(
                    userId,
                    password
            );

        } catch (Exception e) {

            System.out.println(
                    "Password update failed: " + e.getMessage()
            );

            model.addAttribute(
                    "passwordError",
                    "Something went wrong. Please try again."
            );

            return "voter/password-handler";
        }

        UserProfile userProfile =
                userProfileRepository
                        .findByAuthUserId(
                                java.util.UUID.fromString(userId)
                        )
                        .orElse(null);

        String role = (String) session.getAttribute("role");

        if (userProfile != null) {

            userProfile.setMustChangePassword(false);

            userProfileRepository.save(userProfile);

            session.setAttribute("mustChangePassword", false);

            // role may have changed since login (e.g. promoted to CANDIDATE
            // mid-session isn't realistic, but keep source-of-truth consistent)
            role = userProfile.getRole().name();
        }

        return "CANDIDATE".equals(role)
                ? "redirect:/candidate/personal-information"
                : "redirect:/voter/ssc-election";
    }
}