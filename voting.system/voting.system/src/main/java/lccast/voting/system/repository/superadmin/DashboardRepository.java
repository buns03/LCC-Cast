package lccast.voting.system.repository.superadmin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import lccast.voting.system.model.Voter;

/**
 * Single source of dashboard numbers for superadmin, admin-ssc and admin-dept.
 * campusId == null -> all campuses; program == null -> all programs.
 *
 * Counted election = status ACTIVE, i.e. NOT archived, soft-deleted or
 * permanently deleted. Upcoming, ongoing and concluded elections all count:
 * end_at is deliberately NOT checked, so results stay on the dashboard after
 * an election ends. Ballots, candidates and votes only count when their
 * election is counted.
 *
 * Native queries never receive null parameters (Hibernate binds untyped nulls
 * as bytea on PostgreSQL); "all" is passed as a boolean flag instead.
 */
public interface DashboardRepository extends JpaRepository<Voter, UUID> {

    UUID NO_CAMPUS = new UUID(0L, 0L);

    // ---------- public API (unchanged signatures) ----------

    default long countVoters(UUID campusId, String program) {
        return countVotersQ(campusId == null, orZero(campusId), program == null, orEmpty(program));
    }

    default long countVoted(UUID campusId, String program) {
        return countVotedQ(campusId == null, orZero(campusId), program == null, orEmpty(program));
    }

    default long countActiveElections(UUID campusId, String program) {
        return countElectionsQ(campusId == null, orZero(campusId), program == null, orEmpty(program));
    }

    default long countCandidates(UUID campusId, String program) {
        return countCandidatesQ(campusId == null, orZero(campusId), program == null, orEmpty(program));
    }

    default List<Object[]> votedByProgram(UUID campusId) {
        return votedByProgramQ(campusId == null, orZero(campusId));
    }

    private static UUID orZero(UUID id) {
        return id == null ? NO_CAMPUS : id;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    // ---------- queries ----------

    @Query(value = "SELECT c.id FROM campuses c WHERE LOWER(c.name) = LOWER(:name)",
            nativeQuery = true)
    Optional<UUID> findCampusIdByName(@Param("name") String name);

    @Query(value = """
            SELECT COUNT(*) FROM voters v
            WHERE CAST(v.status AS text) = 'ACTIVE'
              AND (:allCampuses = TRUE OR v.campus_id = :campusId)
              AND (:allPrograms = TRUE OR UPPER(v.program_course) = UPPER(:program))
            """, nativeQuery = true)
    long countVotersQ(@Param("allCampuses") boolean allCampuses,
                      @Param("campusId") UUID campusId,
                      @Param("allPrograms") boolean allPrograms,
                      @Param("program") String program);

    @Query(value = """
            SELECT COUNT(DISTINCT b.voter_id)
            FROM ballots b
            JOIN elections e ON e.id = b.election_id
                 AND CAST(e.status AS text) = 'ACTIVE'
            JOIN voters v ON v.id = b.voter_id AND CAST(v.status AS text) = 'ACTIVE'
            WHERE (:allCampuses = TRUE OR v.campus_id = :campusId)
              AND (:allPrograms = TRUE OR UPPER(v.program_course) = UPPER(:program))
            """, nativeQuery = true)
    long countVotedQ(@Param("allCampuses") boolean allCampuses,
                     @Param("campusId") UUID campusId,
                     @Param("allPrograms") boolean allPrograms,
                     @Param("program") String program);

    @Query(value = """
        SELECT COUNT(*) FROM elections e
        WHERE CAST(e.status AS text) = 'ACTIVE'
          AND (:allCampuses = TRUE OR e.campus_id = :campusId)
          AND (:allPrograms = TRUE
               OR EXISTS (
                    SELECT 1 FROM election_departments ed
                    JOIN departments d ON d.id = ed.department_id
                    WHERE ed.election_id = e.id
                      AND (:allCampuses = TRUE OR d.campus_id = :campusId)
                      AND UPPER(d.name) = UPPER(:program))
               OR EXISTS (
                    SELECT 1 FROM candidates c
                    JOIN departments d ON d.id = c.department_id
                    WHERE c.election_id = e.id
                      AND (:allCampuses = TRUE OR d.campus_id = :campusId)
                      AND UPPER(d.name) = UPPER(:program)))
        """, nativeQuery = true)
    long countElectionsQ(@Param("allCampuses") boolean allCampuses,
                         @Param("campusId") UUID campusId,
                         @Param("allPrograms") boolean allPrograms,
                         @Param("program") String program);

    @Query(value = """
        SELECT COUNT(*) FROM candidates c
        JOIN elections e ON e.id = c.election_id
             AND CAST(e.status AS text) = 'ACTIVE'
        WHERE (:allCampuses = TRUE OR e.campus_id = :campusId)
          AND (:allPrograms = TRUE OR EXISTS (
                SELECT 1 FROM departments d
                WHERE d.id = c.department_id
                  AND (:allCampuses = TRUE OR d.campus_id = :campusId)
                  AND UPPER(d.name) = UPPER(:program)))
        """, nativeQuery = true)
    long countCandidatesQ(@Param("allCampuses") boolean allCampuses,
                          @Param("campusId") UUID campusId,
                          @Param("allPrograms") boolean allPrograms,
                          @Param("program") String program);

    /** [campusName, votedCount] for every active campus (0 when nobody voted). */
    @Query(value = """
            SELECT ca.name, COUNT(DISTINCT b.voter_id)
            FROM campuses ca
            LEFT JOIN voters v ON v.campus_id = ca.id AND CAST(v.status AS text) = 'ACTIVE'
            LEFT JOIN ballots b ON b.voter_id = v.id
                 AND b.election_id IN (SELECT e.id FROM elections e
                                       WHERE CAST(e.status AS text) = 'ACTIVE')
            WHERE CAST(ca.status AS text) = 'ACTIVE'
            GROUP BY ca.id, ca.name
            ORDER BY ca.name
            """, nativeQuery = true)
    List<Object[]> votedByCampus();

    /** [programCourse, votedCount] */
    @Query(value = """
            SELECT v.program_course, COUNT(DISTINCT b.voter_id)
            FROM ballots b
            JOIN elections e ON e.id = b.election_id
                 AND CAST(e.status AS text) = 'ACTIVE'
            JOIN voters v ON v.id = b.voter_id AND CAST(v.status AS text) = 'ACTIVE'
            WHERE v.program_course IS NOT NULL
              AND (:allCampuses = TRUE OR v.campus_id = :campusId)
            GROUP BY v.program_course
            ORDER BY v.program_course
            """, nativeQuery = true)
    List<Object[]> votedByProgramQ(@Param("allCampuses") boolean allCampuses,
                                   @Param("campusId") UUID campusId);

    /** [position, voteCount] for one department's candidates, counted elections only. */
    @Query(value = """
        SELECT COALESCE(bv.position, c.position), COUNT(*)
        FROM ballot_votes bv
        JOIN ballots b ON b.id = bv.ballot_id
        JOIN elections e ON e.id = b.election_id
             AND CAST(e.status AS text) = 'ACTIVE'
        JOIN candidates c ON c.id = bv.candidate_id AND c.election_id = e.id
        JOIN departments d ON d.id = c.department_id
        WHERE d.campus_id = :campusId
          AND UPPER(d.name) = UPPER(:program)
        GROUP BY COALESCE(bv.position, c.position)
        """, nativeQuery = true)
    List<Object[]> votesByPosition(@Param("campusId") UUID campusId,
                                   @Param("program") String program);
}