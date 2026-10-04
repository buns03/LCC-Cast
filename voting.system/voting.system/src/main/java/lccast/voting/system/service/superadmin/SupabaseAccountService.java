// New file: src/main/java/lccast/voting/system/service/superadmin/SupabaseAccountService.java
package lccast.voting.system.service.superadmin;

import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.service.SupabaseAuthService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SupabaseAccountService {

    private final UserProfileRepository userProfileRepository;
    private final SupabaseAuthService supabaseAuthService;

    public SupabaseAccountService(UserProfileRepository userProfileRepository,
                                  SupabaseAuthService supabaseAuthService) {
        this.userProfileRepository = userProfileRepository;
        this.supabaseAuthService = supabaseAuthService;
    }

    @Async("supabaseExecutor")
    public void disableAccounts(List<UUID> authUserIds) {
        for (UUID authUserId : authUserIds) {
            userProfileRepository.findByAuthUserId(authUserId).ifPresent(profile -> {
                profile.setActive(false);
                userProfileRepository.save(profile);
            });
            try {
                supabaseAuthService.setUserBanStatus(authUserId.toString(), "876000h");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}