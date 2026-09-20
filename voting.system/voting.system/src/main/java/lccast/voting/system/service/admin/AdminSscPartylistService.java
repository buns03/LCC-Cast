package lccast.voting.system.service.admin;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.CampusRepository;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.service.superadmin.PartylistService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminSscPartylistService {

    private final PartylistService partylistService;
    private final CampusRepository campusRepository;
    private final VoterRepository voterRepository;

    public AdminSscPartylistService(
            PartylistService partylistService,
            CampusRepository campusRepository,
            VoterRepository voterRepository
    ) {
        this.partylistService = partylistService;
        this.campusRepository = campusRepository;
        this.voterRepository = voterRepository;
    }

    // ============ READ ============

    public List<Partylist> getActivePartylists(UUID campusId) {
        return partylistService.getByCampus(campusId);
    }

    public Partylist getById(UUID id, UUID campusId) {
        Partylist partylist = partylistService.getById(id);
        assertOwnedByCampus(partylist, campusId);
        return partylist;
    }

    public List<PartylistMember> getMembers(UUID partylistId, UUID campusId) {
        Partylist partylist = getById(partylistId, campusId); // ownership check
        return partylistService.getMembers(partylist.getId());
    }

    public PartylistMember getMember(UUID partylistId, UUID memberId, UUID campusId) {
        getById(partylistId, campusId); // ownership check
        return partylistService.getMember(partylistId, memberId);
    }

    public List<Voter> getVotersForCampus(UUID campusId) {
        return voterRepository.findByCampusIdAndStatus(campusId, RecordStatus.ACTIVE);
    }

    public Optional<Voter> findVoterByStudentId(String studentId, UUID campusId) {
        return voterRepository.findByStudentId(studentId)
                .filter(voter -> campusId.equals(voter.getCampusId()));
    }

    // ============ WRITE ============

    public Partylist save(Partylist partylist, UUID campusId, HttpServletRequest request) {
        partylist.setCampus(requireCampus(campusId)); // ignore any campus the client sent
        return partylistService.save(partylist, request);
    }

    public Partylist update(UUID id, Partylist updated, UUID campusId, HttpServletRequest request) {
        getById(id, campusId); // ownership check on the EXISTING record
        updated.setCampus(requireCampus(campusId));
        return partylistService.update(id, updated, request);
    }

    public void archive(UUID id, UUID campusId, HttpServletRequest request) {
        getById(id, campusId);
        partylistService.archive(id, request);
    }

    public void delete(UUID id, UUID campusId, HttpServletRequest request) {
        getById(id, campusId);
        partylistService.delete(id, request);
    }

    public void restore(UUID id, UUID campusId, HttpServletRequest request) {
        getById(id, campusId);
        partylistService.restore(id, request);
    }

    public void validateMembers(UUID partylistId, UUID campusId, String schoolYear, List<PartylistMember> members) {
        partylistService.validateMembers(partylistId, campusId, schoolYear, members);
    }

    private Campus requireCampus(UUID campusId) {
        return campusRepository.findById(campusId)
                .orElseThrow(() -> new RuntimeException("Your campus could not be found."));
    }

    private void assertOwnedByCampus(Partylist partylist, UUID campusId) {
        if (partylist.getCampus() == null ||
                partylist.getCampus().getId() == null ||
                !partylist.getCampus().getId().equals(campusId)) {
            throw new RuntimeException("You are not authorized to access this partylist.");
        }
    }
}