package lccast.voting.system.repository;

import lccast.voting.system.model.ElectionDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ElectionDepartmentRepository
        extends JpaRepository<ElectionDepartment, UUID> {

    List<ElectionDepartment> findByElectionId(UUID electionId);

    List<ElectionDepartment> findByDepartmentId(UUID departmentId);

    void deleteByElectionId(UUID electionId);

    boolean existsByElectionIdAndDepartmentId(
            UUID electionId,
            UUID departmentId
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT ed FROM ElectionDepartment ed " +
                    "JOIN FETCH ed.department d " +
                    "JOIN FETCH ed.election e " +
                    "WHERE e.category = :category " +
                    "AND d.status = :status"
    )
    List<ElectionDepartment> findAllForDepartmentElections(
            @org.springframework.data.repository.query.Param("category") lccast.voting.system.model.ElectionCategory category,
            @org.springframework.data.repository.query.Param("status") lccast.voting.system.model.RecordStatus status
    );
}