package lccast.voting.system.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import lccast.voting.system.model.BallotVote;

public interface BallotVoteRepository extends JpaRepository<BallotVote, UUID> {

    List<BallotVote> findByBallot_Id(UUID ballotId);

    long countByCandidate_Id(UUID candidateId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT bv.candidate.id, COUNT(bv) FROM BallotVote bv " +
                    "WHERE bv.candidate.electionId = :electionId " +
                    "GROUP BY bv.candidate.id"
    )
    List<Object[]> countVotesByCandidateForElection(UUID electionId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT bv.candidate.partylistId, COUNT(bv) FROM BallotVote bv " +
                    "WHERE bv.candidate.electionId = :electionId " +
                    "AND bv.candidate.partylistId IS NOT NULL " +
                    "GROUP BY bv.candidate.partylistId"
    )
    List<Object[]> countVotesByPartylistForElection(UUID electionId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(DISTINCT bv.ballot.id) FROM BallotVote bv " +
                    "WHERE bv.candidate.electionId = :electionId " +
                    "AND bv.candidate.departmentId = :departmentId"
    )
    long countDistinctBallotsForElectionAndDepartment(UUID electionId, UUID departmentId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(DISTINCT bv.ballot.id) FROM BallotVote bv " +
                    "WHERE bv.candidate.electionId = :electionId"
    )
    long countDistinctBallotsForElection(@org.springframework.data.repository.query.Param("electionId") UUID electionId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT bv.candidate.id, bv.position, COUNT(bv) FROM BallotVote bv " +
                    "WHERE bv.candidate.electionId = :electionId " +
                    "AND bv.candidate.departmentId = :departmentId " +
                    "GROUP BY bv.candidate.id, bv.position"
    )
    List<Object[]> countVotesByCandidateAndPositionForElectionAndDepartment(
            @org.springframework.data.repository.query.Param("electionId") UUID electionId,
            @org.springframework.data.repository.query.Param("departmentId") UUID departmentId
    );

    void deleteByBallot_IdIn(List<UUID> ballotIds);

}