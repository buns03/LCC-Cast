package lccast.voting.system.service;

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

/**
 * Keeps UserProfile.role in sync with candidacy.
 *
 * ASSUMPTIONS (I don't have UserRole.java, VoterRepository, CandidateRepository,
 * PartylistMemberRepository, or DepartmentMemberRepository to check against):
 *  - UserRole has a CANDIDATE constant. SecurityConfig already calls
 *    .hasRole("CANDIDATE") for /candidate/**, so this almost certainly exists.
 *  - VoterRepository needs a findByStudentId(String) method (see
 *    repository-additions.txt).
 *  - CandidateRepository / PartylistMemberRepository / DepartmentMemberRepository
 *    each need an existsByStudentId(String) method (see repository-additions.txt).
 *
 * INTEGRATION (see integration-notes.txt for exact call sites):
 *  - Call promoteToCandidate(studentId) right after a Candidate, PartylistMember,
 *    or DepartmentMember row is created for that student.
 *  - Call demoteToVoterIfNoLongerCandidate(studentId) right after such a row is
 *    deleted (directly, or as a cascade of an election/partylist/department
 *    being deleted).
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

    @Transactional
    public void promoteToCandidate(String studentId) {
        UserProfile profile = resolveProfile(studentId);
        if (profile == null) return;

        if (profile.getRole() == UserRole.STUDENT) {
            profile.setRole(UserRole.CANDIDATE);
            userProfileRepository.save(profile);
        }
    }

    @Transactional
    public void demoteToVoterIfNoLongerCandidate(String studentId) {
        UserProfile profile = resolveProfile(studentId);
        if (profile == null) return;

        if (profile.getRole() != UserRole.CANDIDATE) return;

        boolean stillCandidate =
                candidateRepository.existsByStudentId(studentId) ||
                        partylistMemberRepository.existsByStudentId(studentId) ||
                        departmentMemberRepository.existsByStudentId(studentId);

        if (!stillCandidate) {
            profile.setRole(UserRole.STUDENT);
            userProfileRepository.save(profile);
        }
    }

    private UserProfile resolveProfile(String studentId) {
        if (studentId == null || studentId.isBlank()) return null;

        Voter voter = voterRepository.findByStudentId(studentId).orElse(null);
        if (voter == null || voter.getAuthUserId() == null) return null;

        return userProfileRepository.findByAuthUserId(voter.getAuthUserId()).orElse(null);
    }
}