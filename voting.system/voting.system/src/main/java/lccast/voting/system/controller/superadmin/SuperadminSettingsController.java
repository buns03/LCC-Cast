package lccast.voting.system.controller.superadmin;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.service.SupabaseStorageService;
import lccast.voting.system.util.SessionUserUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/superadmin")
public class SuperadminSettingsController {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_SIZE = 5L * 1024 * 1024; // 5 MB

    private final UserProfileRepository userProfileRepository;
    private final SupabaseStorageService storageService;

    public SuperadminSettingsController(UserProfileRepository userProfileRepository,
                                        SupabaseStorageService storageService) {
        this.userProfileRepository = userProfileRepository;
        this.storageService = storageService;
    }

    @GetMapping("/settings")
    public String settings(HttpSession session, Model model) {

        String role = (String) session.getAttribute("role");

        if (role == null || !role.equals("SUPERADMIN")) {
            return "redirect:/login";
        }

        String firstName = (String) session.getAttribute("firstName");
        String displayName = (firstName != null && !firstName.trim().isEmpty())
                ? firstName : role;

        String email = null;
        UUID authUserId = SessionUserUtil.getAuthUserId(session);
        if (authUserId != null) {
            email = userProfileRepository.findByAuthUserId(authUserId)
                    .map(UserProfile::getEmail)
                    .orElse(null);
        }

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);
        model.addAttribute("email", email);
        // profilePictureUrl is added by ProfilePictureInterceptor

        return "superadmin/settings.html";
    }

    @PostMapping("/settings/profile-picture")
    @ResponseBody
    public ResponseEntity<?> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        String role = (String) session.getAttribute("role");
        if (role == null || !role.equals("SUPERADMIN")) {
            return ResponseEntity.status(403).body(Map.of("message", "Not allowed."));
        }

        UUID authUserId = SessionUserUtil.getAuthUserId(session);
        if (authUserId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Session expired. Please log in again."));
        }

        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Account not found."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is required."));
        }
        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Only PNG, JPG, or WEBP images are allowed."));
        }
        if (file.getSize() > MAX_SIZE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Image must be 5 MB or smaller."));
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
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Failed to upload profile picture."));
        }
    }
}