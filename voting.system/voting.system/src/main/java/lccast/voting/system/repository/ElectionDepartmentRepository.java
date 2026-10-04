package lccast.voting.system.repository;

import lccast.voting.system.model.ElectionDepartment;
import lccast.voting.system.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
    select count(ed) > 0
    from ElectionDepartment ed
    where ed.department.id = :departmentId
      and ed.election.schoolYear = :schoolYear
      and ed.election.status = :status
      and ed.election.id <> :excludeElectionId
""")
    boolean existsActiveForSchoolYear(
            @Param("departmentId") UUID departmentId,
            @Param("schoolYear") String schoolYear,
            @Param("status") RecordStatus status,
            @Param("excludeElectionId") UUID excludeElectionId
    );

}