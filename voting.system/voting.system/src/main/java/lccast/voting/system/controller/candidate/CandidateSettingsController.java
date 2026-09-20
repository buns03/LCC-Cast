package lccast.voting.system.controller.candidate;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.service.SupabaseAuthResponse;
import lccast.voting.system.service.SupabaseAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/candidate")
public class CandidateSettingsController {

    private final SupabaseAuthService supabaseAuthService;
    private final UserProfileRepository userProfileRepository;
    private final VoterRepository voterRepository; // ADDED

    public CandidateSettingsController(
            SupabaseAuthService supabaseAuthService,
            UserProfileRepository userProfileRepository,
            VoterRepository voterRepository) { // ADDED
        this.supabaseAuthService = supabaseAuthService;
        this.userProfileRepository = userProfileRepository;
        this.voterRepository = voterRepository; // ADDED
    }

    @GetMapping("/settings")
    public String settings(HttpSession session, Model model) {
        String firstName = (String) session.getAttribute("firstName");
        String role = (String) session.getAttribute("role");
        String authUserIdStr = (String) session.getAttribute("userId");

        String email = null;

        if (authUserIdStr != null) {
            UUID authUserId = UUID.fromString(authUserIdStr);
            // CHANGED: was UserProfile (login email) — now Voter (their own contact email)
            Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);
            if (voter != null) {
                email = voter.getEmail();
            }
        }

        String displayName = (firstName != null && !firstName.isBlank()) ? firstName : role;

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);
        model.addAttribute("email", email);

        return "candidate/settings.html";
    }

    @PostMapping("/settings")
    @ResponseBody
    public ResponseEntity<?> updateSettings(
            @RequestBody CandidateSettingsRequest request,
            HttpSession session) {

        String authUserIdStr = (String) session.getAttribute("userId");
        if (authUserIdStr == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Session expired. Please log in again."));
        }

        UUID authUserId = UUID.fromString(authUserIdStr);

        // CHANGED: the record being edited is now the Voter row, not UserProfile
        Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);
        if (voter == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Account not found."));
        }

        boolean wantsEmailChange = request.getNewEmail() != null && !request.getNewEmail().trim().isBlank();
        boolean wantsPasswordChange = request.getNewPassword() != null && !request.getNewPassword().isBlank();

        if (!wantsEmailChange && !wantsPasswordChange) {
            return ResponseEntity.ok(Map.of("message", "Settings saved successfully."));
        }

        if (wantsPasswordChange) {
            String currentPassword = request.getCurrentPassword();
            String newPassword = request.getNewPassword();
            String confirmPassword = request.getConfirmPassword();

            if (currentPassword == null || currentPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Current password is required to change your password."));
            }
            if (newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "New password is required."));
            }
            if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
                return ResponseEntity.badRequest().body(Map.of("message", "New password and confirmation do not match."));
            }

            // CHANGED: password verification still needs the Supabase Auth LOGIN
            // email, which is intentionally separate from voters.email now.
            // UserProfile stays the source of truth for that login email.
            UserProfile profile = userProfileRepository.findByAuthUserId(authUserId).orElse(null);
            if (profile == null) {
                return ResponseEntity.status(404).body(Map.of("message", "Account not found."));
            }

            SupabaseAuthResponse verifyResponse = supabaseAuthService.login(profile.getEmail(), currentPassword);

            if (verifyResponse == null || verifyResponse.getUser() == null) {
                return ResponseEntity.status(403).body(Map.of("message", "Current password is incorrect."));
            }
        }

        if (wantsEmailChange) {
            String newEmail = request.getNewEmail().trim();

            // CHANGED: this now updates ONLY the voter's own contact email.
            // No call to supabaseAuthService.updateEmail() — login credentials
            // are untouched by this field.
            voter.setEmail(newEmail);
            voter.setUpdatedAt(Instant.now());
            voterRepository.save(voter);
        }

        if (wantsPasswordChange) {
            supabaseAuthService.updatePassword(authUserIdStr, request.getNewPassword());
        }

        return ResponseEntity.ok(Map.of("message", "Settings updated successfully."));
    }
}
