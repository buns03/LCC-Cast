package lccast.voting.system.service;

import lccast.voting.system.model.SystemSetting;
import lccast.voting.system.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

@Service
public class SystemSettingService {

    private final SystemSettingRepository repository;
    private final SupabaseStorageService storageService;

    public SystemSettingService(
            SystemSettingRepository repository,
            SupabaseStorageService storageService
    ) {
        this.repository = repository;
        this.storageService = storageService;
    }

    public SystemSetting getSettings() {
        return repository.findAll()
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    SystemSetting settings = new SystemSetting();
                    settings.setSystemName("LCC Cast");
                    settings.setAcademicYear("2026-2027");
                    settings.setDescription("LCC Cast Student Voting System");
                    return repository.save(settings);
                });
    }

    public SystemSetting updateSettings(
            String systemName,
            String academicYear,
            String description
    ) {
        SystemSetting settings = getSettings();

        settings.setSystemName(systemName);
        settings.setAcademicYear(academicYear);
        settings.setDescription(description);

        return repository.save(settings);
    }

    public String uploadLogo(
            org.springframework.web.multipart.MultipartFile file
    ) throws Exception {

        String logoPath = storageService.uploadFile(file, "logo");

        SystemSetting settings = getSettings();
        settings.setLogoPath(logoPath);

        repository.save(settings);

        return logoPath;
    }
}