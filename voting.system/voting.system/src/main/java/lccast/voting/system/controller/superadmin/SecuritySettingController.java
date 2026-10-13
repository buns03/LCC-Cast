package lccast.voting.system.controller.superadmin;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.service.SecuritySettingService;
import lccast.voting.system.util.SessionUserUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/superadmin/api/security-settings")
public class SecuritySettingController {

    private final SecuritySettingService securitySettingService;

    public SecuritySettingController(SecuritySettingService securitySettingService) {
        this.securitySettingService = securitySettingService;
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body,
                                            HttpSession session) {
        try {
            UUID authUserId = SessionUserUtil.getAuthUserId(session);

            securitySettingService.changePassword(
                    authUserId,
                    body.get("currentPassword"),
                    body.get("newPassword")
            );
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/email")
    public ResponseEntity<?> changeEmail(@RequestBody Map<String, String> body,
                                         HttpSession session) {
        try {
            UUID authUserId = SessionUserUtil.getAuthUserId(session);

            String email = securitySettingService.changeEmail(authUserId, body.get("newEmail"));
            session.setAttribute("email", email);

            return ResponseEntity.ok(Map.of("email", email));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Could not update email. It may already be in use.");
        }
    }
}