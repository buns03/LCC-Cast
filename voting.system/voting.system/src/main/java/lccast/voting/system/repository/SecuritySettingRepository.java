package lccast.voting.system.repository;

import lccast.voting.system.model.SecuritySetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SecuritySettingRepository extends JpaRepository<SecuritySetting, UUID> {
}