package lccast.voting.system.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import lccast.voting.system.model.VoteLog;
import lccast.voting.system.model.VotingStatus;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VoteLogRepository extends JpaRepository<VoteLog, UUID>, JpaSpecificationExecutor<VoteLog> {

    long countByVoteStatus(VotingStatus voteStatus);

    long countByVoteStatusAndCampusId(
            VotingStatus voteStatus,
            UUID campusId
    );

    long countByCampusId(UUID campusId);

    boolean existsByElectionIdAndVoterId(UUID electionId, UUID voterId);

    List<VoteLog> findByElectionIdAndVoterIdOrderByVotedAtDesc(UUID electionId, UUID voterId);

    default Optional<VoteLog> findByElectionIdAndVoterId(UUID electionId, UUID voterId) {
        List<VoteLog> results = findByElectionIdAndVoterIdOrderByVotedAtDesc(electionId, voterId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @org.springframework.data.jpa.repository.Query(
            "SELECT vl.yearLevel, COUNT(vl) FROM VoteLog vl " +
                    "WHERE vl.electionId = :electionId GROUP BY vl.yearLevel"
    )
    List<Object[]> countByElectionIdGroupByYearLevel(
            @org.springframework.data.repository.query.Param("electionId") UUID electionId
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT vl.programCourse, COUNT(vl) FROM VoteLog vl " +
                    "WHERE vl.electionId = :electionId GROUP BY vl.programCourse"
    )
    List<Object[]> countByElectionIdGroupByProgram(
            @org.springframework.data.repository.query.Param("electionId") UUID electionId
    );

    boolean existsByBallotId(UUID ballotId);

    Optional<VoteLog> findByBallotId(UUID ballotId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT vl.programCourse FROM VoteLog vl " +
                    "WHERE vl.programCourse IS NOT NULL ORDER BY vl.programCourse"
    )
    List<String> findDistinctProgramCourses();

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT vl.section FROM VoteLog vl " +
                    "WHERE vl.section IS NOT NULL ORDER BY vl.section"
    )
    List<String> findDistinctSections();

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT vl.yearLevel FROM VoteLog vl " +
                    "WHERE vl.yearLevel IS NOT NULL ORDER BY vl.yearLevel"
    )
    List<String> findDistinctYearLevels();

    void deleteByElectionId(UUID electionId);

    List<VoteLog> findByElectionIdIn(Collection<UUID> electionIds);

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT v.section FROM VoteLog v " +
                    "WHERE v.campusId = :campusId AND UPPER(v.programCourse) = UPPER(:programCourse) " +
                    "AND v.section IS NOT NULL ORDER BY v.section"
    )
    List<String> findDistinctSectionsByCampusAndProgram(
            @org.springframework.data.repository.query.Param("campusId") UUID campusId,
            @org.springframework.data.repository.query.Param("programCourse") String programCourse
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT v.yearLevel FROM VoteLog v " +
                    "WHERE v.campusId = :campusId AND UPPER(v.programCourse) = UPPER(:programCourse) " +
                    "AND v.yearLevel IS NOT NULL ORDER BY v.yearLevel"
    )
    List<String> findDistinctYearLevelsByCampusAndProgram(
            @org.springframework.data.repository.query.Param("campusId") UUID campusId,
            @org.springframework.data.repository.query.Param("programCourse") String programCourse
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT v.programCourse FROM VoteLog v " +
                    "WHERE v.campusId = :campusId AND v.programCourse IS NOT NULL " +
                    "ORDER BY v.programCourse"
    )
    List<String> findDistinctProgramCoursesByCampus(
            @org.springframework.data.repository.query.Param("campusId") UUID campusId
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT v.section FROM VoteLog v " +
                    "WHERE v.campusId = :campusId AND v.section IS NOT NULL " +
                    "ORDER BY v.section"
    )
    List<String> findDistinctSectionsByCampus(
            @org.springframework.data.repository.query.Param("campusId") UUID campusId
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT v.yearLevel FROM VoteLog v " +
                    "WHERE v.campusId = :campusId AND v.yearLevel IS NOT NULL " +
                    "ORDER BY v.yearLevel"
    )
    List<String> findDistinctYearLevelsByCampus(
            @org.springframework.data.repository.query.Param("campusId") UUID campusId
    );



}