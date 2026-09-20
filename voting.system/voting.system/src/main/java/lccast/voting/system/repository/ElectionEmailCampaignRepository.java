package lccast.voting.system.repository;

import lccast.voting.system.model.ElectionEmailCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElectionEmailCampaignRepository extends JpaRepository<ElectionEmailCampaign, UUID> {

    Optional<ElectionEmailCampaign> findFirstByElectionIdAndStatusOrderByCreatedAtDesc(
            UUID electionId, String status);

    List<ElectionEmailCampaign> findByStatusAndScheduledAtLessThanEqual(
            String status, Instant now);
}