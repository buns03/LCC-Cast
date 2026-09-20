package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.controller.adminDept.AdminSettingsRequest;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.service.SupabaseAuthResponse;
import lccast.voting.system.service.SupabaseAuthService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin-ssc")
public class AdminSscSettingsController {

    private final SupabaseAuthService supabaseAuthService;
    private final UserProfileRepository userProfileRepository;
    private final SupabaseStorageService storageService;

    public AdminSscSettingsController(
            SupabaseAuthService supabaseAuthService,
            UserProfileRepository userProfileRepository,
            SupabaseStorageService storageService) {
        this.supabaseAuthService = supabaseAuthService;
        this.userProfileRepository = userProfileRepository;
        this.storageService = storageService;
    }

    // =====================================================
    // GET — RENDER SETTINGS PAGE
    // =====================================================
    @GetMapping("/settings")
    public String settings(HttpSession session, Model model) {

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

        String displayName = (firstName != null && !firstName.isBlank()) ? firstName : role;

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);
        model.addAttribute("programCourse", programCourse);
        model.addAttribute("campus", campus);
        model.addAttribute("email", email);
        model.addAttribute("profilePictureUrl", profilePictureUrl);

        return "admin-ssc/settings.html";
    }

    // =====================================================
    // POST — UPDATE EMAIL / PASSWORD
    // =====================================================
    @PostMapping("/settings")
    @ResponseBody
    public ResponseEntity<?> updateSettings(
            @RequestBody AdminSettingsRequest request,
            HttpSession session) {

        String authUserIdStr = (String) session.getAttribute("userId");

        if (authUserIdStr == null) {
            return ResponseEntity.status(401).body(
                    Map.of("message", "Session expired. Please log in again.")
            );
        }

        UUID authUserId = UUID.fromString(authUserIdStr);

        UserProfile profile =
                userProfileRepository.findByAuthUserId(authUserId).orElse(null);

        if (profile == null) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "Admin account not found.")
            );
        }

        String currentEmail = profile.getEmail();

        // =====================================================
        // DETERMINE WHICH FIELDS ACTUALLY HAVE CHANGES
        // Blank = NO CHANGE
        // =====================================================

        boolean wantsEmailChange =
                request.getNewEmail() != null &&
                        !request.getNewEmail().trim().isBlank();

        boolean wantsPasswordChange =
                request.getNewPassword() != null &&
                        !request.getNewPassword().isBlank();

        // =====================================================
        // NOTHING TO CHANGE
        // Still allow Save
        // =====================================================

        if (!wantsEmailChange && !wantsPasswordChange) {
            return ResponseEntity.ok(
                    Map.of("message", "Settings saved successfully.")
            );
        }

        // =====================================================
        // PASSWORD CHANGE VALIDATION
        // Current password is REQUIRED ONLY for password change
        // =====================================================

        if (wantsPasswordChange) {

            String currentPassword = request.getCurrentPassword();
            String newPassword = request.getNewPassword();
            String confirmPassword = request.getConfirmPassword();

            if (currentPassword == null || currentPassword.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "Current password is required to change your password.")
                );
            }

            if (newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "New password is required.")
                );
            }

            if (confirmPassword == null ||
                    !confirmPassword.equals(newPassword)) {

                return ResponseEntity.badRequest().body(
                        Map.of("message", "New password and confirmation do not match.")
                );
            }

            // =====================================================
            // VERIFY CURRENT PASSWORD
            // ONLY FOR PASSWORD CHANGE
            // =====================================================

            SupabaseAuthResponse verifyResponse =
                    supabaseAuthService.login(
                            currentEmail,
                            currentPassword
                    );

            if (verifyResponse == null ||
                    verifyResponse.getUser() == null) {

                return ResponseEntity.status(403).body(
                        Map.of("message", "Current password is incorrect.")
                );
            }
        }

        // =====================================================
        // UPDATE EMAIL — ONLY IF NOT BLANK
        // =====================================================

        if (wantsEmailChange) {

            String newEmail = request.getNewEmail().trim();

            // Update Supabase Auth
            supabaseAuthService.updateEmail(
                    authUserIdStr,
                    newEmail
            );

            // Update local UserProfile
            profile.setEmail(newEmail);
            profile.setUpdatedAt(Instant.now());

            userProfileRepository.save(profile);

            // Update session
            session.setAttribute("email", newEmail);
        }

        // =====================================================
        // UPDATE PASSWORD — ONLY IF NOT BLANK
        // =====================================================

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

    // =====================================================
    // POST — UPLOAD PROFILE PICTURE
    // =====================================================
    @PostMapping("/settings/profile-picture")
    @ResponseBody
    public ResponseEntity<?> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        String authUserIdStr = (String) session.getAttribute("userId");
        if (authUserIdStr == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Session expired. Please log in again."));
        }

        UUID authUserId = UUID.fromString(authUserIdStr);
        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId).orElse(null);

        if (profile == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Admin account not found."));
        }

        try {
            String storagePath = storageService.uploadFile(file, "profile");

            profile.setProfilePictureUrl(storagePath);
            profile.setUpdatedAt(Instant.now());
            userProfileRepository.save(profile);

            return ResponseEntity.ok(Map.of(
                    "message", "Profile picture updated successfully.",
                    "profilePictureUrl", "/files/" + storagePath
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("message", "Failed to upload profile picture."));
        }
    }
}