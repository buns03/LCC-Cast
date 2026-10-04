package lccast.voting.system.repository;

import lccast.voting.system.model.DepartmentMember;
import lccast.voting.system.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentMemberRepository
        extends JpaRepository<DepartmentMember, UUID> {

    List<DepartmentMember> findByDepartmentId(UUID departmentId);

    List<DepartmentMember> findByStudentId(String studentId);

    List<DepartmentMember> findByStudentIdAndDepartmentSchoolYear(
            String studentId,
            String schoolYear
    );

    boolean existsByStudentIdAndDepartmentSchoolYear(
            String studentId,
            String schoolYear
    );

//    boolean existsByStudentIdAndDepartmentSchoolYearAndIdNot(
//            String studentId,
//            String schoolYear,
//            UUID memberId
//    );

    List<DepartmentMember> findByStudentIdAndDepartmentSchoolYearAndDepartmentStatus(
            String studentId,
            String schoolYear,
            RecordStatus status
    );

    boolean existsByStudentIdAndDepartmentSchoolYearAndDepartmentStatus(
            String studentId,
            String schoolYear,
            RecordStatus status
    );


    void deleteByDepartmentId(UUID departmentId);

    Optional<DepartmentMember> findByDepartmentIdAndStudentId(UUID departmentId, String studentId);

    boolean existsByStudentId(String studentId);

    boolean existsByStudentIdAndDepartmentStatus(String studentId, RecordStatus status);

}