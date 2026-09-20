package lccast.voting.system.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import lccast.voting.system.model.Election;
import lccast.voting.system.model.ElectionCategory;
import lccast.voting.system.model.RecordStatus;

public interface ElectionRepository extends JpaRepository<Election, UUID> {

    long countByActiveTrueAndStatus(
            RecordStatus status
    );

    long countByActiveTrueAndStatusAndCampusId(
            RecordStatus status,
            UUID campusId
    );

    List<Election> findByStatus(
            RecordStatus status
    );

    List<Election> findByCampusIdAndStatus(
            UUID campusId,
            RecordStatus status
    );

    Optional<Election> findByIdAndStatus(
            UUID id,
            RecordStatus status
    );

    List<Election> findBySchoolYearAndStatus(
            String schoolYear,
            RecordStatus status
    );

    List<Election> findByCategoryAndStatus(
            ElectionCategory category,
            RecordStatus status
    );

    Optional<Election> findByActiveTrueAndStatus(
            RecordStatus status
    );

    List<Election> findByCampusIdAndCategoryAndStatus(
            UUID campusId,
            ElectionCategory category,
            RecordStatus status
    );
}