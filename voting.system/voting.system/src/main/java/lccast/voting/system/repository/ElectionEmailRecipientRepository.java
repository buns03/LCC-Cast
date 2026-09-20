package lccast.voting.system.repository;

import lccast.voting.system.model.ElectionEmailRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ElectionEmailRecipientRepository extends JpaRepository<ElectionEmailRecipient, UUID> {
    List<ElectionEmailRecipient> findByCampaignId(UUID campaignId);
}