package lccast.voting.system.controller;

import lccast.voting.system.model.SystemSetting;
import lccast.voting.system.service.SystemSettingService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PublicSettingsController {

    private final SystemSettingService systemSettingService;
    private final SupabaseStorageService storageService;

    public PublicSettingsController(SystemSettingService systemSettingService,
                                    SupabaseStorageService storageService) {
        this.systemSettingService = systemSettingService;
        this.storageService = storageService;
    }

    @GetMapping("/api/public/branding")
    public Map<String, Object> getBranding() {
        SystemSetting s = systemSettingService.getSettings();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("systemName", s.getSystemName() != null ? s.getSystemName() : "LCCast");
        body.put("description", s.getDescription() != null ? s.getDescription() : "");
        body.put("hasLogo", s.getLogoPath() != null && !s.getLogoPath().isBlank());
        return body;
    }

    @GetMapping("/api/public/branding/logo")
    public ResponseEntity<byte[]> getLogo() {
        SystemSetting s = systemSettingService.getSettings();
        if (s.getLogoPath() == null || s.getLogoPath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return storageService.getFile(s.getLogoPath());
    }
}