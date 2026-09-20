package lccast.voting.system.repository;

import lccast.voting.system.model.UserProfile;
import lccast.voting.system.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository
        extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByAuthUserId(UUID authUserId);

    Optional<UserProfile> findBySchoolId(String schoolId);

    Optional<UserProfile> findByEmail(String email);

    List<UserProfile> findByRoleOrderByCreatedAtDesc(UserRole role);

    boolean existsBySchoolId(String schoolId);

    List<UserProfile> findByRole(UserRole role);
}