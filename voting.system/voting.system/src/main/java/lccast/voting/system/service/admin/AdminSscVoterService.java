package lccast.voting.system.service.admin;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.CampusRepository;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.service.superadmin.VoterService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminSscVoterService {

    private final VoterRepository voterRepository;
    private final VoterService voterService;
    private final CampusRepository campusRepository;

    public AdminSscVoterService(
            VoterRepository voterRepository,
            VoterService voterService,
            CampusRepository campusRepository
    ) {
        this.voterRepository = voterRepository;
        this.voterService = voterService;
        this.campusRepository = campusRepository;
    }

    // ============ READ ============

    public List<Map<String, Object>> getActiveForCampus(UUID campusId) {
        List<Voter> voters = voterRepository.findByCampusIdAndStatus(campusId, RecordStatus.ACTIVE);
        return voterService.toResponseList(voters);
    }

    public Map<String, Object> getById(UUID id, UUID campusId) {
        Voter voter = requireOwned(id, campusId);
        return voterService.toResponse(voter);
    }

    public Optional<Map<String, Object>> getByStudentId(String studentId, UUID campusId) {
        return voterRepository.findByStudentId(studentId.trim())
                .filter(v -> v.getStatus() == RecordStatus.ACTIVE)
                .filter(v -> campusId.equals(v.getCampusId()))
                .map(voterService::toResponse);
    }

    // ============ WRITE ============

    public Map<String, Object> update(UUID id, UUID campusId, Map<String, Object> request, HttpServletRequest httpRequest) {

        Voter voter = requireOwned(id, campusId);

        String studentId = stringValue(request.get("studentId"));
        if (studentId.isBlank()) {
            throw new IllegalArgumentException("Student ID is required.");
        }

        voterRepository.findByStudentId(studentId).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new IllegalArgumentException("This Student ID is already registered.");
            }
        });

        voter.setStudentId(studentId);
        voter.setLastName(stringValue(request.get("lastName")));
        voter.setFirstName(stringValue(request.get("firstName")));
        voter.setMiddleName(stringValue(request.get("middleName")));
        voter.setFullName(stringValue(request.get("fullName")));
        voter.setEmail(stringValue(request.get("email")));
        voter.setProgramCourse(stringValue(request.get("programCourse")));
        voter.setYearLevel(stringValue(request.get("yearLevel")));
        voter.setSection(stringValue(request.get("section")));

        // Campus is NOT taken from the request — it's locked to the admin's own campus.
        voter.setCampusId(campusId);

        Voter saved = voterRepository.save(voter);
        voterService.auditUpdate(httpRequest, saved);

        return voterService.toResponse(saved);
    }

    public void archive(UUID id, UUID campusId, HttpServletRequest request) {
        requireOwned(id, campusId);
        voterService.archiveVoter(id, request);
    }

    public void delete(UUID id, UUID campusId, HttpServletRequest request) {
        requireOwned(id, campusId);
        voterService.deleteVoter(id, request);
    }

    public void restore(UUID id, UUID campusId, HttpServletRequest request) {
        Voter voter = voterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Voter not found."));
        if (!campusId.equals(voter.getCampusId())) {
            throw new RuntimeException("You are not authorized to restore this voter.");
        }
        if (voter.getStatus() != RecordStatus.DELETED && voter.getStatus() != RecordStatus.ARCHIVED) {
            throw new IllegalArgumentException("Only deleted or archived voters can be restored.");
        }
        voter.setStatus(RecordStatus.ACTIVE);
        Voter restored = voterRepository.save(voter);
        voterService.restoreVoterRecords(restored.getId());
        voterService.auditRestore(request, restored);
    }

    public int archiveAllInCampus(UUID campusId, List<UUID> ids, HttpServletRequest request) {
        List<UUID> owned = filterOwnedIds(ids, campusId);
        return voterService.archiveVoters(owned, request);
    }

    public int deleteAllInCampus(UUID campusId, List<UUID> ids, HttpServletRequest request) {
        List<UUID> owned = filterOwnedIds(ids, campusId);
        return voterService.deleteVoters(owned, request);
    }

    public VoterService.ImportResult importForCampus(UUID campusId, List<Map<String, Object>> rows, HttpServletRequest request) {
        // Force every row's campusId to the admin's own campus, regardless of what the file said.
        List<Map<String, Object>> scopedRows = rows.stream()
                .map(row -> {
                    Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
                    copy.put("campusId", campusId.toString());
                    return copy;
                })
                .toList();

        return voterService.importVoters(scopedRows, request);
    }

    // ============ HELPERS ============

    private Voter requireOwned(UUID id, UUID campusId) {
        Voter voter = voterRepository.findByIdAndStatus(id, RecordStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Voter not found."));
        if (!campusId.equals(voter.getCampusId())) {
            throw new RuntimeException("You are not authorized to access this voter.");
        }
        return voter;
    }

    private List<UUID> filterOwnedIds(List<UUID> ids, UUID campusId) {
        if (ids == null || ids.isEmpty()) return List.of();
        return voterRepository.findAllById(ids).stream()
                .filter(v -> campusId.equals(v.getCampusId()))
                .map(Voter::getId)
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}