package lccast.voting.system.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lccast.voting.system.model.ElectionCategory;

import org.springframework.data.jpa.repository.JpaRepository;

import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.Voter;
import lccast.voting.system.model.VotingStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoterRepository extends JpaRepository<Voter, UUID> {

    long countByStatus(RecordStatus status);

    long countByStatusAndVotingStatus(
            RecordStatus status,
            VotingStatus votingStatus
    );

    long countByStatusAndCampusId(
            RecordStatus status,
            UUID campusId
    );

    long countByStatusAndVotingStatusAndCampusId(
            RecordStatus status,
            VotingStatus votingStatus,
            UUID campusId
    );

    List<Voter> findByStatus(
            RecordStatus status
    );

    List<Voter> findByCampusIdAndStatus(
            UUID campusId,
            RecordStatus status
    );

    Optional<Voter> findByIdAndStatus(
            UUID id,
            RecordStatus status
    );

    Optional<Voter> findByStudentId(
            String studentId
    );

    Optional<Voter> findByStudentIdAndCampusId(
            String studentId,
            UUID campusId
    );

    Optional<Voter> findByAuthUserId(
            UUID authUserId
    );

    Optional<Voter> findByIdAndCampusIdAndStatus(
            UUID id,
            UUID campusId,
            RecordStatus status
    );

    Optional<Voter> findByStudentIdAndCampusIdAndProgramCourse(
            String studentId,
            UUID campusId,
            String programCourse
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT v.yearLevel, COUNT(v) FROM Voter v " +
                    "WHERE v.campusId = :campusId " +
                    "AND v.votingStatus = :votingStatus " +
                    "GROUP BY v.yearLevel"
    )
    List<Object[]> countVotedByYearLevelAndCampus(
            UUID campusId,
            VotingStatus votingStatus
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT v.programCourse, COUNT(v) FROM Voter v " +
                    "WHERE v.campusId = :campusId " +
                    "AND v.votingStatus = :votingStatus " +
                    "GROUP BY v.programCourse"
    )
    List<Object[]> countVotedByProgramAndCampus(
            UUID campusId,
            VotingStatus votingStatus
    );

    @org.springframework.data.jpa.repository.Query(
            "SELECT v.yearLevel, COUNT(v) FROM Voter v " +
                    "WHERE v.campusId = :campusId " +
                    "AND v.programCourse = :programCourse " +
                    "AND v.votingStatus = :votingStatus " +
                    "GROUP BY v.yearLevel"
    )
    List<Object[]> countVotedByYearLevelForDepartment(
            UUID campusId,
            String programCourse,
            VotingStatus votingStatus
    );

    @Query("SELECT v.yearLevel, COUNT(v) FROM Voter v " +
            "WHERE v.campusId = :campusId AND v.programCourse IN :deptNames " +
            "AND v.votingStatus = :status GROUP BY v.yearLevel")
    List<Object[]> countVotedByYearLevelForDepartments(
            @Param("campusId") UUID campusId,
            @Param("deptNames") List<String> deptNames,
            @Param("status") VotingStatus status);

    @Query("SELECT v FROM Voter v WHERE v.status = :status AND v.campusId = :campusId " +
            "AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:programCourse))")
    List<Voter> findByCampusIdAndProgramCourseAndStatus(
            @Param("campusId") UUID campusId,
            @Param("programCourse") String programCourse,
            @Param("status") RecordStatus status);

    @Query("SELECT v FROM Voter v WHERE v.id = :id AND v.status = :status AND v.campusId = :campusId " +
            "AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:programCourse))")
    Optional<Voter> findByIdAndCampusIdAndProgramCourseAndStatus(
            @Param("id") UUID id,
            @Param("campusId") UUID campusId,
            @Param("programCourse") String programCourse,
            @Param("status") RecordStatus status);

    List<Voter> findByCampusIdAndProgramCourseInAndStatus(
            UUID campusId, List<String> programCourses, RecordStatus status);

    @Query("SELECT v FROM Voter v WHERE v.status = :status AND v.campusId = :campusId " +
            "AND LOWER(TRIM(v.programCourse)) IN :programCourses")
    List<Voter> findByCampusIdAndProgramCourseInIgnoreCaseAndStatus(
            @Param("campusId") UUID campusId,
            @Param("programCourses") List<String> programCourses,
            @Param("status") RecordStatus status);

    Optional<Voter> findByEmailIgnoreCase(String email);

    // =========================================================
    // BULK STATUS UPDATE — single SQL UPDATE instead of N saves
    // =========================================================

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE Voter v SET v.status = :status, v.updatedAt = CURRENT_TIMESTAMP " +
                    "WHERE v.id IN :ids"
    )
    int bulkUpdateStatus(@Param("ids") List<UUID> ids, @Param("status") RecordStatus status);

    // =========================================================
    // PAGINATED + FILTERED LIST (replaces "load everything")
    // =========================================================

    @Query("""
        SELECT v FROM Voter v
        WHERE v.status = :status
        AND (:campusId IS NULL OR v.campusId = :campusId)
        AND (:program IS NULL OR v.programCourse = :program)
        AND (:yearLevel IS NULL OR v.yearLevel = :yearLevel)
        AND (:section IS NULL OR v.section = :section)
        AND (:search IS NULL OR
             LOWER(v.fullName) LIKE CONCAT('%', CAST(:search AS string), '%') OR
             LOWER(v.studentId) LIKE CONCAT('%', CAST(:search AS string), '%'))
        """)
    org.springframework.data.domain.Page<Voter> search(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("program") String program,
            @Param("yearLevel") String yearLevel,
            @Param("section") String section,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable
    );

    // =========================================================
    // ID-ONLY LOOKUP FOR BULK ACTIONS — server resolves ids itself,
    // client never needs to hold the full list in memory
    // =========================================================

    @Query("""
        SELECT v.id FROM Voter v
        WHERE v.status = :status
        AND (:campusId IS NULL OR v.campusId = :campusId)
        AND (:program IS NULL OR v.programCourse = :program)
        AND (:yearLevel IS NULL OR v.yearLevel = :yearLevel)
        AND (:section IS NULL OR v.section = :section)
        AND (:search IS NULL OR
             LOWER(v.fullName) LIKE CONCAT('%', CAST(:search AS string), '%') OR
             LOWER(v.studentId) LIKE CONCAT('%', CAST(:search AS string), '%'))
        """)
    List<UUID> findIdsMatching(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("program") String program,
            @Param("yearLevel") String yearLevel,
            @Param("section") String section,
            @Param("search") String search
    );

    // =========================================================
    // DISTINCT FACETS — for filter dropdowns, without loading all rows
    // =========================================================

    @Query("SELECT DISTINCT v.programCourse FROM Voter v WHERE v.status = :status AND (:campusId IS NULL OR v.campusId = :campusId) AND v.programCourse IS NOT NULL ORDER BY v.programCourse")
    List<String> findDistinctPrograms(@Param("status") RecordStatus status, @Param("campusId") UUID campusId);

    @Query("SELECT DISTINCT v.section FROM Voter v WHERE v.status = :status AND (:campusId IS NULL OR v.campusId = :campusId) AND v.section IS NOT NULL ORDER BY v.section")
    List<String> findDistinctSections(@Param("status") RecordStatus status, @Param("campusId") UUID campusId);

    @Query("""
        SELECT v FROM Voter v
        WHERE v.status = :status
        AND v.campusId = :campusId
        AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:programCourse))
        AND (:yearLevel IS NULL OR v.yearLevel = :yearLevel)
        AND (:section IS NULL OR v.section = :section)
        AND (:search IS NULL OR
             LOWER(v.fullName) LIKE CONCAT('%', CAST(:search AS string), '%') OR
             LOWER(v.studentId) LIKE CONCAT('%', CAST(:search AS string), '%'))
        """)
    org.springframework.data.domain.Page<Voter> searchByCampusAndProgram(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("programCourse") String programCourse,
            @Param("yearLevel") String yearLevel,
            @Param("section") String section,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
        SELECT v.id FROM Voter v
        WHERE v.status = :status
        AND v.campusId = :campusId
        AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:programCourse))
        AND (:yearLevel IS NULL OR v.yearLevel = :yearLevel)
        AND (:section IS NULL OR v.section = :section)
        AND (:search IS NULL OR
             LOWER(v.fullName) LIKE CONCAT('%', CAST(:search AS string), '%') OR
             LOWER(v.studentId) LIKE CONCAT('%', CAST(:search AS string), '%'))
        """)
    List<UUID> findIdsMatchingByCampusAndProgram(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("programCourse") String programCourse,
            @Param("yearLevel") String yearLevel,
            @Param("section") String section,
            @Param("search") String search
    );

    @Query("SELECT DISTINCT v.section FROM Voter v WHERE v.status = :status AND (:campusId IS NULL OR v.campusId = :campusId) AND (:program IS NULL OR v.programCourse = :program) AND v.section IS NOT NULL ORDER BY v.section")
    List<String> findDistinctSections(@Param("status") RecordStatus status, @Param("campusId") UUID campusId, @Param("program") String program);

    @Query("SELECT DISTINCT v.yearLevel FROM Voter v WHERE v.status = :status AND v.campusId = :campusId AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:programCourse)) AND v.yearLevel IS NOT NULL ORDER BY v.yearLevel")
    List<String> findDistinctYearLevelsForDepartment(@Param("status") RecordStatus status, @Param("campusId") UUID campusId, @Param("programCourse") String programCourse);

    @Query("SELECT DISTINCT v.section FROM Voter v WHERE v.status = :status AND v.campusId = :campusId AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:programCourse)) AND v.section IS NOT NULL ORDER BY v.section")
    List<String> findDistinctSectionsForDepartment(@Param("status") RecordStatus status, @Param("campusId") UUID campusId, @Param("programCourse") String programCourse);

    @Query("SELECT DISTINCT v.section FROM Voter v WHERE v.status = :status " +
            "AND (:campusId IS NULL OR v.campusId = :campusId) " +
            "AND v.programCourse IN :programs AND v.section IS NOT NULL ORDER BY v.section")
    List<String> findDistinctSectionsForPrograms(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("programs") List<String> programs);

    @Query("""
    SELECT v FROM Voter v
    WHERE v.status = :status
    AND (:campusId IS NULL OR v.campusId = :campusId)
    AND (:program IS NULL OR v.programCourse = :program)
    AND (:yearLevel IS NULL OR v.yearLevel = :yearLevel)
    AND (:section IS NULL OR v.section = :section)
    AND (:search IS NULL OR
         LOWER(v.fullName) LIKE CONCAT('%', CAST(:search AS string), '%') OR
         LOWER(v.studentId) LIKE CONCAT('%', CAST(:search AS string), '%'))
    AND (:sscVoted IS NULL OR
         (:sscVoted = TRUE AND v.id IN :sscVotedIds) OR
         (:sscVoted = FALSE AND v.id NOT IN :sscVotedIds))
    AND (:deptVoted IS NULL OR
         (:deptVoted = TRUE AND v.id IN :deptVotedIds) OR
         (:deptVoted = FALSE AND v.id NOT IN :deptVotedIds))
    ORDER BY
      CASE WHEN :timeSort = 'Newest' THEN
        (SELECT MAX(vl.votedAt) FROM VoteLog vl, Election e
         WHERE vl.voterId = v.id AND e.id = vl.electionId AND e.status = :status
         AND ((:includeSsc = TRUE AND e.category = :sscCategory)
           OR (:includeDept = TRUE AND e.category <> :sscCategory))) END DESC NULLS LAST,
      CASE WHEN :timeSort = 'Oldest' THEN
        (SELECT MAX(vl.votedAt) FROM VoteLog vl, Election e
         WHERE vl.voterId = v.id AND e.id = vl.electionId AND e.status = :status
         AND ((:includeSsc = TRUE AND e.category = :sscCategory)
           OR (:includeDept = TRUE AND e.category <> :sscCategory))) END ASC NULLS LAST,
      CASE WHEN :nameSort = 'A-Z' THEN LOWER(v.fullName) END ASC,
      CASE WHEN :nameSort = 'Z-A' THEN LOWER(v.fullName) END DESC,
      v.id ASC
    """)
    org.springframework.data.domain.Page<Voter> searchWithVotingStatus(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("program") String program,
            @Param("yearLevel") String yearLevel,
            @Param("section") String section,
            @Param("search") String search,
            @Param("sscVoted") Boolean sscVoted,
            @Param("sscVotedIds") List<UUID> sscVotedIds,
            @Param("deptVoted") Boolean deptVoted,
            @Param("deptVotedIds") List<UUID> deptVotedIds,
            @Param("nameSort") String nameSort,
            @Param("timeSort") String timeSort,
            @Param("includeSsc") boolean includeSsc,
            @Param("includeDept") boolean includeDept,
            @Param("sscCategory") ElectionCategory sscCategory,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
    SELECT v FROM Voter v
    WHERE v.status = :status
    AND v.campusId = :campusId
    AND LOWER(TRIM(v.programCourse)) = LOWER(TRIM(:programCourse))
    AND (:yearLevel IS NULL OR v.yearLevel = :yearLevel)
    AND (:section IS NULL OR v.section = :section)
    AND (:search IS NULL OR
         LOWER(v.fullName) LIKE CONCAT('%', CAST(:search AS string), '%') OR
         LOWER(v.studentId) LIKE CONCAT('%', CAST(:search AS string), '%'))
    AND (:deptVoted IS NULL OR
         (:deptVoted = TRUE AND v.id IN :deptVotedIds) OR
         (:deptVoted = FALSE AND v.id NOT IN :deptVotedIds))
    ORDER BY
      CASE WHEN :timeSort = 'Newest' THEN
        (SELECT MAX(vl.votedAt) FROM VoteLog vl, Election e
         WHERE vl.voterId = v.id AND e.id = vl.electionId AND e.status = :status
         AND e.category <> :sscCategory) END DESC NULLS LAST,
      CASE WHEN :timeSort = 'Oldest' THEN
        (SELECT MAX(vl.votedAt) FROM VoteLog vl, Election e
         WHERE vl.voterId = v.id AND e.id = vl.electionId AND e.status = :status
         AND e.category <> :sscCategory) END ASC NULLS LAST,
      CASE WHEN :nameSort = 'A-Z' THEN LOWER(v.fullName) END ASC,
      CASE WHEN :nameSort = 'Z-A' THEN LOWER(v.fullName) END DESC,
      v.id ASC
    """)
    org.springframework.data.domain.Page<Voter> searchByCampusAndProgramWithVotingStatus(
            @Param("status") RecordStatus status,
            @Param("campusId") UUID campusId,
            @Param("programCourse") String programCourse,
            @Param("yearLevel") String yearLevel,
            @Param("section") String section,
            @Param("search") String search,
            @Param("deptVoted") Boolean deptVoted,
            @Param("deptVotedIds") List<UUID> deptVotedIds,
            @Param("nameSort") String nameSort,
            @Param("timeSort") String timeSort,
            @Param("sscCategory") ElectionCategory sscCategory,
            org.springframework.data.domain.Pageable pageable
    );
}