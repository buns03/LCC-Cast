package lccast.voting.system.repository;

import lccast.voting.system.model.Department;
import lccast.voting.system.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByStatus(RecordStatus status);

    List<Department> findByCampusIdAndStatus(
            UUID campusId,
            RecordStatus status
    );

    Optional<Department> findByIdAndStatus(
            UUID id,
            RecordStatus status
    );

    List<Department> findBySchoolYearAndStatus(
            String schoolYear,
            RecordStatus status
    );

    Optional<Department> findFirstByCampusId(UUID campusId);

    boolean existsByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndIdNot(
            String title,
            UUID id
    );

    Optional<Department> findByTitleIgnoreCaseAndCampusId(
            String title,
            UUID campusId
    );

    Optional<Department> findByCodeIgnoreCase(String code);

    List<Department> findByCampusIdAndNameIgnoreCaseAndStatus(
            UUID campusId,
            String name,
            RecordStatus status
    );

//    Optional<Department> findByCampusIdAndNameIgnoreCase(UUID campusId, String name);


}