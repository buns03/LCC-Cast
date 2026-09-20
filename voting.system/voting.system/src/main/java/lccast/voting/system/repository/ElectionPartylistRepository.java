package lccast.voting.system.repository;

import lccast.voting.system.model.ElectionPartylist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ElectionPartylistRepository
        extends JpaRepository<ElectionPartylist, UUID> {

    List<ElectionPartylist> findByElectionId(UUID electionId);

    void deleteByElectionId(UUID electionId);
}