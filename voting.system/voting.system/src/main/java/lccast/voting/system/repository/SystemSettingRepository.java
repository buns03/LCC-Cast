package lccast.voting.system.repository;

import lccast.voting.system.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {
}