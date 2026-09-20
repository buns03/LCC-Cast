package lccast.voting.system.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

}