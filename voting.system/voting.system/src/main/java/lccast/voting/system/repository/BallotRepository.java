package lccast.voting.system.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import lccast.voting.system.model.Ballot;

// ANONYMITY CONSTRAINT: keep this interface limited to the duplicate-vote
// check below. Do not add a method that returns a full Ballot by voter —
// that's a ready-made path from voter_id to candidate_id via BallotVote.
// Tallying/results code must aggregate BallotVote by candidate_id/election_id
// only, never by voter.
public interface BallotRepository extends JpaRepository<Ballot, UUID> {

    boolean existsByElection_IdAndVoter_Id(
            UUID electionId,
            UUID voterId
    );

    // Voter-only: used exclusively by the authenticated voter's own
    // Vote Summaries page. Fetches election eagerly since every summary
    // card needs election.title/category/schoolYear.
    @org.springframework.data.jpa.repository.Query(
            "SELECT b FROM Ballot b " +
                    "JOIN FETCH b.election e " +
                    "WHERE b.voter.id = :voterId " +
                    "ORDER BY b.submittedAt DESC"
    )
    List<Ballot> findByVoter_IdOrderBySubmittedAtDesc(UUID voterId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT b FROM Ballot b WHERE b.election.id = :electionId ORDER BY b.submittedAt ASC"
    )
    List<Ballot> findByElection_IdOrderBySubmittedAtAsc(UUID electionId);

    boolean existsByVoter_Id(UUID voterId);
    void deleteByElection_Id(UUID electionId);

}