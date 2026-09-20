package lccast.voting.system.repository;

import lccast.voting.system.model.Campus;
import lccast.voting.system.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampusRepository
        extends JpaRepository<Campus, UUID> {

    List<Campus> findByStatus(RecordStatus status);

    Optional<Campus> findByNameIgnoreCase(String name);

}