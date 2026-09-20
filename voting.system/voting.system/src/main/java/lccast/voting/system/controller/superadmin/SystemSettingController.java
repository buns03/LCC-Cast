package lccast.voting.system.controller.superadmin;

import lccast.voting.system.model.SystemSetting;
import lccast.voting.system.service.SystemSettingService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/superadmin/api/system-settings")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;
    private final SupabaseStorageService storageService;

    public SystemSettingController(SystemSettingService systemSettingService,
                                   SupabaseStorageService storageService) {
        this.systemSettingService = systemSettingService;
        this.storageService = storageService;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        SystemSetting s = systemSettingService.getSettings();
        return Map.of(
                "systemName", s.getSystemName() != null ? s.getSystemName() : "",
                "academicYear", s.getAcademicYear() != null ? s.getAcademicYear() : "",
                "description", s.getDescription() != null ? s.getDescription() : "",
                "logoPath", s.getLogoPath() != null ? s.getLogoPath() : ""
        );
    }

    @PutMapping
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, String> body) {
        try {
            SystemSetting updated = systemSettingService.updateSettings(
                    body.get("systemName"),
                    body.get("academicYear"),
                    body.get("description")
            );
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/logo")
    public ResponseEntity<?> uploadLogo(@RequestParam("file") MultipartFile file) {
        try {
            String path = systemSettingService.uploadLogo(file);
            return ResponseEntity.ok(Map.of("logoPath", path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> getLogo() {
        SystemSetting s = systemSettingService.getSettings();
        if (s.getLogoPath() == null || s.getLogoPath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return storageService.getFile(s.getLogoPath());
    }
}