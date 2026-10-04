package lccast.voting.system.controller.student;

import jakarta.servlet.http.HttpSession;

import lccast.voting.system.model.UserProfile;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.service.CandidatePortalService;
import lccast.voting.system.service.SupabaseAuthResponse;
import lccast.voting.system.service.SupabaseAuthService;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/voter")
public class SettingsController {

    private final SupabaseAuthService supabaseAuthService;
    private final UserProfileRepository userProfileRepository;
    private final VoterRepository voterRepository;
    private final CandidatePortalService candidatePortalService;

    public SettingsController(
            SupabaseAuthService supabaseAuthService,
            UserProfileRepository userProfileRepository,
            VoterRepository voterRepository,
            CandidatePortalService candidatePortalService) {

        this.supabaseAuthService = supabaseAuthService;
        this.userProfileRepository = userProfileRepository;
        this.voterRepository = voterRepository;
        this.candidatePortalService = candidatePortalService;
    }

    // =====================================================
    // GET — RENDER SETTINGS PAGE
    // =====================================================
    @GetMapping("/settings")
    public String settings(
            HttpSession session,
            Model model) {

        String firstName = (String) session.getAttribute("firstName");
        String role = (String) session.getAttribute("role");
        String programCourse = (String) session.getAttribute("programCourse");
        String campus = (String) session.getAttribute("campus");

        String authUserIdStr = (String) session.getAttribute("userId");

        String email = null;

        if (authUserIdStr != null) {
            UUID authUserId = UUID.fromString(authUserIdStr);

            Voter voter = voterRepository
                    .findByAuthUserId(authUserId)
                    .orElse(null);

            if (voter != null) {
                email = voter.getEmail();
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
        model.addAttribute("email", email);
        model.addAttribute("avatarUrl", resolveAvatarUrl((String) session.getAttribute("userId"))); // ADD

        return "voter/settings.html";
    }

    private String resolveAvatarUrl(String authUserIdStr) {
        if (authUserIdStr == null) {
            return null;
        }
        String storagePath = candidatePortalService.findAvatarStoragePath(UUID.fromString(authUserIdStr));
        if (storagePath == null) {
            return null;
        }
        return "/api/storage/file?path=" + URLEncoder.encode(storagePath, StandardCharsets.UTF_8);
    }

    // =====================================================
    // POST — UPDATE EMAIL / PASSWORD
    // =====================================================
    @PostMapping("/settings")
    @ResponseBody
    public ResponseEntity<?> updateSettings(
            @RequestBody VoterSettingsRequest request,
            HttpSession session) {

        String authUserIdStr = (String) session.getAttribute("userId");

        if (authUserIdStr == null) {
            return ResponseEntity.status(401).body(
                    Map.of("message", "Session expired. Please log in again.")
            );
        }

        UUID authUserId = UUID.fromString(authUserIdStr);

        Voter voter = voterRepository
                .findByAuthUserId(authUserId)
                .orElse(null);

        if (voter == null) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "Voter account not found.")
            );
        }

        String currentEmail = voter.getEmail();

        boolean wantsEmailChange =
                request.getNewEmail() != null &&
                        !request.getNewEmail().isBlank();

        boolean wantsPasswordChange =
                request.getCurrentPassword() != null &&
                        !request.getCurrentPassword().isBlank();

        if (!wantsEmailChange && !wantsPasswordChange) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "No changes submitted.")
            );
        }

        if (wantsPasswordChange) {

            String newPassword = request.getNewPassword();
            String confirmPassword = request.getConfirmPassword();

            if (newPassword == null || newPassword.isBlank() ||
                    confirmPassword == null ||
                    !confirmPassword.equals(newPassword)) {

                return ResponseEntity.badRequest().body(
                        Map.of("message", "New password and confirmation do not match.")
                );
            }
        }

        if (wantsPasswordChange) {

            String newPassword = request.getNewPassword();
            String confirmPassword = request.getConfirmPassword();

            if (newPassword == null || newPassword.isBlank() ||
                    confirmPassword == null ||
                    !confirmPassword.equals(newPassword)) {

                return ResponseEntity.badRequest().body(
                        Map.of("message", "New password and confirmation do not match.")
                );
            }

            UserProfile profile = userProfileRepository.findByAuthUserId(authUserId).orElse(null);
            if (profile == null || profile.getEmail() == null) {
                return ResponseEntity.status(404).body(
                        Map.of("message", "Account login could not be found.")
                );
            }

            try {
                supabaseAuthService.login(profile.getEmail(), request.getCurrentPassword());
            } catch (Exception e) {
                return ResponseEntity.status(403).body(
                        Map.of("message", "Current password is incorrect.")
                );
            }
        }

        if (wantsEmailChange) {

            String newEmail = request.getNewEmail().trim();

            voter.setEmail(newEmail);
            voter.setUpdatedAt(java.time.Instant.now());

            voterRepository.save(voter);

            session.setAttribute("email", newEmail);
        }

        if (wantsPasswordChange) {

            supabaseAuthService.updatePassword(
                    authUserIdStr,
                    request.getNewPassword()
            );
        }

        return ResponseEntity.ok(
                Map.of("message", "Settings updated successfully.")
        );
    }
}