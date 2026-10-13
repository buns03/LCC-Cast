package lccast.voting.system.service;

import lccast.voting.system.model.UserProfile;
import lccast.voting.system.repository.UserProfileRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class SecuritySettingService {

    private final UserProfileRepository userProfileRepository;
    private final SupabaseAuthService supabaseAuthService;

    public SecuritySettingService(UserProfileRepository userProfileRepository,
                                  SupabaseAuthService supabaseAuthService) {
        this.userProfileRepository = userProfileRepository;
        this.supabaseAuthService = supabaseAuthService;
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

    public String changeEmail(UUID authUserId, String newEmail) {
        if (authUserId == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        String email = newEmail == null ? "" : newEmail.trim();

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }

        if (email.equalsIgnoreCase(profile.getEmail())) {
            throw new IllegalArgumentException("New email is the same as your current email.");
        }

        // Supabase Auth first; if it rejects (e.g. already in use) nothing local changes
        supabaseAuthService.updateEmail(authUserId.toString(), email);

        profile.setEmail(email);
        profile.setUpdatedAt(Instant.now());
        userProfileRepository.save(profile);

        return email;
    }
}