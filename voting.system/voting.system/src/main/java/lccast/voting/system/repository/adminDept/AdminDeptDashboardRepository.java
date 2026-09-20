package lccast.voting.system.repository.adminDept;

import lccast.voting.system.dto.adminDept.PositionVoteData;
import lccast.voting.system.model.Department;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.VotingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * All queries here scope by (campusId, departmentCode) rather than a stored
 * department_id, because user_profiles.department_id is never populated for
 * DEPARTMENT admins (see AdminService.createAdmin) and, even if it were, a
 * "departments" row is tied to one school_year/election cycle rather than
 * being a stable identity for a program like "BSBA". admin_department /
 * departments.code is the stable key across cycles.
 *
 * Based on Department rather than Voter: every query here is fundamentally
 * "given a department's (campus, code) identity, count/list something",
 * and basing it on the wrong entity is exactly what let an earlier derived
 * query method (countCandidatesByDepartmentId, with no @Query) silently
 * target Voter instead of Candidate.
 */
@Repository
public interface AdminDeptDashboardRepository extends JpaRepository<Department, UUID> {

    // Voters have no department granularity — campus + program_course match
    // is the closest available proxy. Confirm voters.program_course actually
    // stores the same codes as admin_department ("BSBA" etc.) before relying
    // on this for anything user-facing; if it's freer text this undercounts
    // silently rather than erroring.
    @Query("""
        SELECT COUNT(v.id) FROM Voter v
        WHERE v.status = :status
          AND v.campusId = :campusId
          AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:departmentCode))
        """)
    long countActiveVotersByCampusAndProgram(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("departmentCode") String departmentCode);

    @Query("""
        SELECT COUNT(v.id) FROM Voter v
        WHERE v.status = :status
          AND v.votingStatus = :votingStatus
          AND v.campusId = :campusId
          AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:departmentCode))
        """)
    long countVotedByCampusAndProgram(
            @Param("status") RecordStatus status,
            @Param("votingStatus") VotingStatus votingStatus,
            @Param("campusId") UUID campusId,
            @Param("departmentCode") String departmentCode);

    // Elections linked to any departments row matching this campus + code,
    // across school years (matches how superadmin's own campus-level counts
    // also aren't year-filtered). Add a school_year param later if "this
    // cycle only" turns out to be what's wanted.
    @Query("""
        SELECT COUNT(DISTINCT e.id)
        FROM Election e
        JOIN ElectionDepartment ed ON ed.election.id = e.id
        JOIN ed.department d
        WHERE e.status = :status
          AND d.campus.id = :campusId
          AND d.code = :departmentCode
        """)
    long countActiveElectionsByDepartmentCode(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("departmentCode") String departmentCode);

    // Candidates belonging to any departments row matching this campus + code.
    // Candidate.departmentId is a raw UUID column, not a @ManyToOne — so this
    // must be an explicit join to Department by id, not a path join.
    @Query("""
        SELECT COUNT(c.id)
        FROM Candidate c
        JOIN Department d ON d.id = c.departmentId
        WHERE d.campus.id = :campusId
          AND d.code = :departmentCode
        """)
    long countCandidatesByDepartmentCode(
            @Param("campusId") UUID campusId,
            @Param("departmentCode") String departmentCode);

    // Votes per position for this department's candidates
    @Query("""
        SELECT new lccast.voting.system.dto.adminDept.PositionVoteData(
            c.position, COUNT(bv.id))
        FROM Candidate c
        JOIN Department d ON d.id = c.departmentId
        LEFT JOIN BallotVote bv ON bv.candidate.id = c.id
        WHERE d.campus.id = :campusId
          AND d.code = :departmentCode
        GROUP BY c.position
        """)
    List<PositionVoteData> getVotesByPositionForDepartmentCode(
            @Param("campusId") UUID campusId,
            @Param("departmentCode") String departmentCode);
}