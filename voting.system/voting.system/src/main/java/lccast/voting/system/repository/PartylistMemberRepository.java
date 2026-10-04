package lccast.voting.system.repository;

import lccast.voting.system.model.PartylistMember;
import lccast.voting.system.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartylistMemberRepository
        extends JpaRepository<PartylistMember, UUID> {

    List<PartylistMember> findByPartylistId(UUID partylistId);

    void deleteByPartylistId(UUID partylistId);

    boolean existsByPartylistIdAndStudentId(
            UUID partylistId,
            String studentId
    );

    boolean existsByStudentIdAndPartylistSchoolYearAndPartylistStatus(
            String studentId,
            String schoolYear,
            RecordStatus status
    );

    boolean existsByStudentIdAndPartylistIdNotAndPartylistSchoolYearAndPartylistStatus(
            String studentId,
            UUID partylistId,
            String schoolYear,
            RecordStatus status
    );

    Optional<PartylistMember> findByPartylistIdAndStudentId(UUID partylistId, String studentId);

    Optional<PartylistMember> findByStudentId(String studentId);

    boolean existsByStudentId(String studentId);

    List<PartylistMember> findAllByStudentId(String studentId);

    boolean existsByStudentIdAndPartylistStatus(String studentId, RecordStatus status);


}