package lccast.voting.system.service;

import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.model.UserRole;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.CandidateRepository;
import lccast.voting.system.repository.DepartmentMemberRepository;
import lccast.voting.system.repository.PartylistMemberRepository;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.repository.VoterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * Keeps UserProfile.role in sync with candidacy.
 *
 * Two kinds of demotion:
 *
 *  1) demoteSscMembers / demoteDepartmentMembers  (NEW)
 *     Used when a PARTYLIST (SSC) or a DEPARTMENT (partylist type or
 *     representative type) is archived, soft-deleted, permanently deleted, or
 *     when a member is removed from it. SSC and Department are separate: only
 *     the SAME kind of candidacy is checked. A member of an archived partylist
 *     is demoted to voter unless they are still an SSC candidate elsewhere
 *     (another ACTIVE partylist, or a candidate in an ACTIVE SSC election).
 *     Being a department member does not keep them a candidate, and vice versa.
 *
 *  2) demoteToVoterIfNoLongerCandidate / demoteAllIfNoLongerCandidate  (unchanged)
 *     Used by election archive/delete and history restore. Keeps the role while
 *     the student has ANY candidacy, SSC or Department.
 */
@Service
public class CandidateRoleSyncService {

    private final VoterRepository voterRepository;
    private final UserProfileRepository userProfileRepository;
    private final CandidateRepository candidateRepository;
    private final PartylistMemberRepository partylistMemberRepository;
    private final DepartmentMemberRepository departmentMemberRepository;

    public CandidateRoleSyncService(
            VoterRepository voterRepository,
            UserProfileRepository userProfileRepository,
            CandidateRepository candidateRepository,
            PartylistMemberRepository partylistMemberRepository,
            DepartmentMemberRepository departmentMemberRepository) {
        this.voterRepository = voterRepository;
        this.userProfileRepository = userProfileRepository;
        this.candidateRepository = candidateRepository;
        this.partylistMemberRepository = partylistMemberRepository;
        this.departmentMemberRepository = departmentMemberRepository;
    }

    // =========================================================
    // SSC (partylist) — separate from Department
    // =========================================================

    @Transactional
    public void demoteSscMembers(Collection<String> studentIds) {
        if (studentIds == null) return;
        studentIds.forEach(this::demoteSscMember);
    }

    @Transactional
    public void demoteSscMember(String studentId) {
        UserProfile profile = resolveProfile(studentId);
        if (profile == null || profile.getRole() != UserRole.CANDIDATE) return;

        boolean stillSscCandidate =
                partylistMemberRepository.existsByStudentIdAndPartylistStatus(studentId, RecordStatus.ACTIVE) ||
                        candidateRepository.existsSscCandidacy(studentId, RecordStatus.ACTIVE);

        if (!stillSscCandidate) {
            demote(profile);
        }
    }

    // =========================================================
    // DEPARTMENT (partylist type and representative type) — separate from SSC
    // =========================================================

    @Transactional
    public void demoteDepartmentMembers(Collection<String> studentIds) {
        if (studentIds == null) return;
        studentIds.forEach(this::demoteDepartmentMember);
    }

    @Transactional
    public void demoteDepartmentMember(String studentId) {
        UserProfile profile = resolveProfile(studentId);
        if (profile == null || profile.getRole() != UserRole.CANDIDATE) return;

        boolean stillDepartmentCandidate =
                departmentMemberRepository.existsByStudentIdAndDepartmentStatus(studentId, RecordStatus.ACTIVE) ||
                        candidateRepository.existsDepartmentCandidacy(studentId, RecordStatus.ACTIVE);

        if (!stillDepartmentCandidate) {
            demote(profile);
        }
    }

    // =========================================================
    // ANY candidacy (elections archive/delete, history restore) — unchanged
    // =========================================================

    @Transactional
    public void demoteToVoterIfNoLongerCandidate(String studentId) {
        UserProfile profile = resolveProfile(studentId);
        if (profile == null) return;
        if (profile.getRole() != UserRole.CANDIDATE) return;

        boolean stillCandidate =
                candidateRepository.existsByStudentIdAndElectionStatus(studentId, RecordStatus.ACTIVE) ||
                        partylistMemberRepository.existsByStudentIdAndPartylistStatus(studentId, RecordStatus.ACTIVE) ||
                        departmentMemberRepository.existsByStudentIdAndDepartmentStatus(studentId, RecordStatus.ACTIVE);

        if (!stillCandidate) {
            demote(profile);
        }
    }

    @Transactional
    public void demoteAllIfNoLongerCandidate(Collection<String> studentIds) {
        if (studentIds == null) return;
        studentIds.forEach(this::demoteToVoterIfNoLongerCandidate);
    }

    @Transactional
    public void promoteAll(Collection<String> studentIds) {
        if (studentIds == null) return;
        studentIds.forEach(this::promoteToCandidate);
    }

    @Transactional
    public void promoteToCandidate(String studentId) {
        UserProfile profile = resolveProfile(studentId);
        if (profile == null) return;

        if (profile.getRole() == UserRole.STUDENT) {
            profile.setRole(UserRole.CANDIDATE);
            userProfileRepository.save(profile);
        }
    }

    // =========================================================
    // helpers
    // =========================================================

    private void demote(UserProfile profile) {
        profile.setRole(UserRole.STUDENT);
        userProfileRepository.save(profile);
    }

    private UserProfile resolveProfile(String studentId) {
        if (studentId == null || studentId.isBlank()) return null;

        Voter voter = voterRepository.findByStudentId(studentId).orElse(null);
        if (voter == null || voter.getAuthUserId() == null) return null;

        return userProfileRepository.findByAuthUserId(voter.getAuthUserId()).orElse(null);
    }
}