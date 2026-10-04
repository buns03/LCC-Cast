package lccast.voting.system.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lccast.voting.system.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import lccast.voting.system.model.Candidate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    long countByElectionId(UUID electionId);

    void deleteByElectionId(UUID electionId);

    List<Candidate> findByElectionId(UUID electionId);

    List<Candidate> findByDepartmentId(UUID departmentId);

    List<Candidate> findByElectionIdAndDepartmentId(UUID electionId, UUID departmentId);

    Optional<Candidate> findByStudentId(String studentId);

    List<Candidate> findAllByStudentId(String studentId);

    boolean existsByStudentId(String studentId);

    List<Candidate> findByPartylistId(UUID partylistId);

    @Query("""
    SELECT COUNT(c) > 0 FROM Candidate c
    JOIN Election e ON e.id = c.electionId
    WHERE c.studentId = :studentId AND e.status = :status
""")
    boolean existsByStudentIdAndElectionStatus(@Param("studentId") String studentId,
                                               @Param("status") RecordStatus status);


    // candidate rows created from a PARTYLIST (SSC) in an election with this status
    @Query("SELECT COUNT(c) > 0 FROM Candidate c " +
            "WHERE c.studentId = :studentId AND c.partylistId IS NOT NULL " +
            "AND c.electionId IN (SELECT e.id FROM Election e WHERE e.status = :status)")
    boolean existsSscCandidacy(@Param("studentId") String studentId, @Param("status") RecordStatus status);

    // candidate rows created from a DEPARTMENT (partylist or representative type)
    @Query("SELECT COUNT(c) > 0 FROM Candidate c " +
            "WHERE c.studentId = :studentId AND c.departmentId IS NOT NULL " +
            "AND c.electionId IN (SELECT e.id FROM Election e WHERE e.status = :status)")
    boolean existsDepartmentCandidacy(@Param("studentId") String studentId, @Param("status") RecordStatus status);

}