package lccast.voting.system.controller.superadmin;

import lccast.voting.system.model.SecuritySetting;
import lccast.voting.system.service.SecuritySettingService;
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

    @GetMapping
    public SecuritySetting getSettings() {
        return securitySettingService.getSettings();
    }

    @PutMapping
    public ResponseEntity<?> updateAutoLogout(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("autoLogoutEnabled");
        SecuritySetting updated = securitySettingService.setAutoLogout(enabled != null && enabled);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> body,
            jakarta.servlet.http.HttpSession session) {
        try {
            Object authUserIdAttr = session.getAttribute("authUserId");

            UUID authUserId = authUserIdAttr instanceof UUID
                    ? (UUID) authUserIdAttr
                    : (authUserIdAttr != null ? UUID.fromString(authUserIdAttr.toString()) : null);

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
}