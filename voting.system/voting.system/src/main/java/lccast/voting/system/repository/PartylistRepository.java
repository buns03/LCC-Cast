package lccast.voting.system.repository;

import lccast.voting.system.model.Partylist;
import lccast.voting.system.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartylistRepository
        extends JpaRepository<Partylist, UUID> {

    List<Partylist> findByStatus(RecordStatus status);

    List<Partylist> findByCampusIdAndStatus(
            UUID campusId,
            RecordStatus status
    );

    boolean existsByNameAndCampusIdAndSchoolYear(
            String name,
            UUID campusId,
            String schoolYear
    );


    Optional<Partylist> findFirstByCampusId(UUID campusId);

    boolean existsByNameIgnoreCaseAndStatus(
            String name,
            RecordStatus status
    );

    boolean existsByNameIgnoreCaseAndStatusAndIdNot(
            String name,
            RecordStatus status,
            UUID id
    );

}