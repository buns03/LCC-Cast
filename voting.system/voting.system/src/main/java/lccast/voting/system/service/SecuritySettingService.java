package lccast.voting.system.service;

import lccast.voting.system.model.SecuritySetting;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.repository.SecuritySettingRepository;
import lccast.voting.system.repository.UserProfileRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SecuritySettingService {

    private final SecuritySettingRepository repository;
    private final UserProfileRepository userProfileRepository;
    private final SupabaseAuthService supabaseAuthService;

    public SecuritySettingService(SecuritySettingRepository repository,
                                  UserProfileRepository userProfileRepository,
                                  SupabaseAuthService supabaseAuthService) {
        this.repository = repository;
        this.userProfileRepository = userProfileRepository;
        this.supabaseAuthService = supabaseAuthService;
    }

    public SecuritySetting getSettings() {
        return repository.findAll().stream()
                .findFirst()
                .orElseGet(() -> repository.save(new SecuritySetting()));
    }

    public SecuritySetting setAutoLogout(boolean enabled) {
        SecuritySetting settings = getSettings();
        settings.setAutoLogoutEnabled(enabled);
        return repository.save(settings);
    }

    public void changePassword(UUID authUserId, String currentPassword, String newPassword) {
        if (authUserId == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        if (profile.getEmail() == null || profile.getAuthUserId() == null) {
            throw new IllegalArgumentException("Account has no linked login.");
        }

        // Verify current password via Supabase Auth
        try {
            supabaseAuthService.login(profile.getEmail(), currentPassword);
        } catch (Exception e) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password cannot be the same as your current password.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }

        supabaseAuthService.updatePassword(profile.getAuthUserId().toString(), newPassword);
    }
}