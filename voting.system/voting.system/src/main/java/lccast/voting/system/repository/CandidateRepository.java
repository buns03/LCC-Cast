package lccast.voting.system.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import lccast.voting.system.model.Candidate;

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

}