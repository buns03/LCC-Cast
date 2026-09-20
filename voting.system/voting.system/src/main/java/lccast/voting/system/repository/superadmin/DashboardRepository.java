package lccast.voting.system.repository.superadmin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lccast.voting.system.dto.adminDept.PositionVoteData;
import lccast.voting.system.model.Campus;
import lccast.voting.system.model.Candidate;
import lccast.voting.system.model.Election;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.Voter;
import lccast.voting.system.model.VoteLog;
import lccast.voting.system.model.VotingStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import lccast.voting.system.dto.superadmin.CampusVoteData;
import lccast.voting.system.dto.superadmin.ProgramVoteData;

@Repository
public interface DashboardRepository
        extends JpaRepository<Voter, UUID> {

    // =====================================================
    // TOTAL VOTERS
    // =====================================================

    @Query("""
        SELECT COUNT(v)
        FROM Voter v
        WHERE v.status = :status
    """)
    long countAllActiveVoters(
            @Param("status") RecordStatus status
    );

    @Query("""
        SELECT COUNT(v)
        FROM Voter v
        WHERE v.status = :status
          AND v.campusId = :campusId
    """)
    long countActiveVotersByCampus(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId
    );


    // =====================================================
    // TOTAL VOTED
    // =====================================================

    @Query("""
        SELECT COUNT(v)
        FROM Voter v
        WHERE v.status = :status
          AND v.votingStatus = :votingStatus
    """)
    long countVoted(
            @Param("status") RecordStatus status,
            @Param("votingStatus") VotingStatus votingStatus
    );

    @Query("""
        SELECT COUNT(v)
        FROM Voter v
        WHERE v.status = :status
          AND v.votingStatus = :votingStatus
          AND v.campusId = :campusId
    """)
    long countVotedByCampus(
            @Param("status") RecordStatus status,
            @Param("votingStatus") VotingStatus votingStatus,
            @Param("campusId") UUID campusId
    );


    // =====================================================
    // ACTIVE ELECTIONS
    // =====================================================

    @Query("""
        SELECT COUNT(e)
        FROM Election e
        WHERE e.active = true
          AND e.status = :status
    """)
    long countActiveElections(
            @Param("status") RecordStatus status
    );

    @Query("""
        SELECT COUNT(e)
        FROM Election e
        WHERE e.active = true
          AND e.status = :status
          AND e.campusId = :campusId
    """)
    long countActiveElectionsByCampus(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId
    );


    // =====================================================
    // TOTAL CANDIDATES
    // =====================================================

    @Query("""
        SELECT COUNT(c)
        FROM Candidate c
    """)
    long countCandidates();

    @Query("""
        SELECT COUNT(c)
        FROM Candidate c
        JOIN Election e
          ON e.id = c.electionId
        WHERE e.campusId = :campusId
    """)
    long countCandidatesByCampus(
            @Param("campusId") UUID campusId
    );


    // =====================================================
    // VOTES BY CAMPUS
    // =====================================================

// =====================================================
// VOTES BY CAMPUS
// =====================================================

    @Query("""
    SELECT new lccast.voting.system.dto.superadmin.CampusVoteData(
        c.name,
        COUNT(v)
    )
    FROM Campus c
    LEFT JOIN Voter v
        ON v.campusId = c.id
        AND v.status = :status
        AND v.votingStatus = :votingStatus
    WHERE LOWER(c.name) IN ('college', 'muzon')
    GROUP BY c.id, c.name
    ORDER BY c.name
""")
    List<CampusVoteData> getVotesByCampus(
            @Param("status") RecordStatus status,
            @Param("votingStatus") VotingStatus votingStatus
    );


    // =====================================================
    // DEPARTMENT / PROGRAM VOTES
    // =====================================================

    @Query("""
        SELECT new lccast.voting.system.dto.superadmin.ProgramVoteData(
            v.programCourse,
            COUNT(vl)
        )
        FROM VoteLog vl
        JOIN Voter v
          ON v.id = vl.voterId
        WHERE v.programCourse IS NOT NULL
        GROUP BY v.programCourse
        ORDER BY COUNT(vl) DESC
    """)
    List<ProgramVoteData> getDepartmentVotes();


    @Query("""
        SELECT new lccast.voting.system.dto.superadmin.ProgramVoteData(
            v.programCourse,
            COUNT(vl)
        )
        FROM VoteLog vl
        JOIN Voter v
          ON v.id = vl.voterId
        WHERE v.programCourse IS NOT NULL
          AND v.campusId = :campusId
        GROUP BY v.programCourse
        ORDER BY COUNT(vl) DESC
    """)
    List<ProgramVoteData> getDepartmentVotesByCampus(
            @Param("campusId") UUID campusId
    );


    // =====================================================
    // CAMPUS LOOKUP
    // =====================================================

    @Query("""
        SELECT c
        FROM Campus c
        WHERE LOWER(c.name) = LOWER(:name)
    """)
    Optional<Campus> findCampusByNameIgnoreCase(
            @Param("name") String name
    );

}