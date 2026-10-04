package lccast.voting.system.service.admin;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.model.Campus;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.CampusRepository;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.service.superadmin.VoterService;
import org.springframework.stereotype.Service;
import lccast.voting.system.model.ElectionCategory;

import java.util.*;

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

    // ============ PAGINATED READ ============

    public Map<String, Object> getPageForCampus(
            UUID campusId,
            int page,
            int size,
            String program,
            String yearLevel,
            String section,
            String search,
            String sscStatus,
            String nameSort,
            String timeSort
    ) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);

        Boolean sscVoted = toVotedFlag(sscStatus);
        List<UUID> sscVotedIds = voterService.safeIdList(
                sscVoted == null ? Set.of() : voterService.resolveSscVotedVoterIds(campusId));
        List<UUID> noDeptFilterIds = voterService.safeIdList(Set.of());

        var result = voterRepository.searchWithVotingStatus(
                RecordStatus.ACTIVE, campusId, blankToNull(program), blankToNull(yearLevel),
                blankToNull(section), blankToNull(search == null ? null : search.toLowerCase()),
                sscVoted, sscVotedIds, null, noDeptFilterIds,
                voterService.normalizeNameSort(nameSort), voterService.normalizeTimeSort(timeSort),
                true, false, ElectionCategory.SSC,   // SSC admin only sees SSC vote time
                pageable
        );

        return Map.of(
                "content", voterService.toResponseList(result.getContent()),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page
        );
    }

    // AdminSscVoterService — still the OLD version
    private Boolean toVotedFlag(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toLowerCase();
        if (s.contains("not")) return Boolean.FALSE;
        if (s.contains("voted")) return Boolean.TRUE;
        return null;
    }

    public List<String> getProgramFacets(UUID campusId) {
        return voterRepository.findDistinctPrograms(RecordStatus.ACTIVE, campusId);
    }

    public List<String> getSectionFacets(UUID campusId, List<String> programs) {
        if (programs == null || programs.isEmpty()) {
            return voterRepository.findDistinctSections(RecordStatus.ACTIVE, campusId, (String) null);
        }
        if (programs.size() == 1) {
            return voterRepository.findDistinctSections(RecordStatus.ACTIVE, campusId, programs.get(0));
        }
        return voterRepository.findDistinctSectionsForPrograms(RecordStatus.ACTIVE, campusId, programs);
    }

    // ============ BULK BY FILTER (no client-side ID list needed) ============

    public int archiveAllMatching(UUID campusId, String program, String yearLevel,
                                  String section, String search, HttpServletRequest request) {
        List<UUID> ids = voterRepository.findIdsMatching(
                RecordStatus.ACTIVE, campusId, blankToNull(program), blankToNull(yearLevel),
                blankToNull(section), blankToNull(search)
        );
        return voterService.archiveVoters(ids, request);
    }

    public int deleteAllMatching(UUID campusId, String program, String yearLevel,
                                 String section, String search, HttpServletRequest request) {
        List<UUID> ids = voterRepository.findIdsMatching(
                RecordStatus.ACTIVE, campusId, blankToNull(program), blankToNull(yearLevel),
                blankToNull(section), blankToNull(search)
        );
        return voterService.deleteVoters(ids, request);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public Map<String, Object> getById(UUID id, UUID campusId) {
        Voter voter = requireOwned(id, campusId);
        return voterService.toResponse(voter);
    }

    public List<Map<String, Object>> exportMatching(UUID campusId, String program, String yearLevel,
                                                    String section, String search) {
        List<UUID> ids = voterRepository.findIdsMatching(
                RecordStatus.ACTIVE, campusId, blankToNull(program), blankToNull(yearLevel),
                blankToNull(section), blankToNull(search)
        );
        if (ids.isEmpty()) return List.of();
        List<Voter> voters = voterRepository.findAllById(ids);
        return voterService.toResponseList(voters);
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

        String campusName = campusRepository.findById(campusId)
                .map(Campus::getName)
                .orElse(null);

        List<Map<String, Object>> scopedRows = new ArrayList<>();
        List<String> skipErrors = new ArrayList<>();
        int skippedForCampusMismatch = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            int rowNumber = i + 2;

            String rowCampus = stringValue(row.get("campus"));

            if (!rowCampus.isBlank() && campusName != null && !rowCampus.equalsIgnoreCase(campusName.trim())) {
                skipErrors.add("Row " + rowNumber + ": Skipped — student belongs to campus \"" + rowCampus
                        + "\", not your campus (\"" + campusName + "\").");
                skippedForCampusMismatch++;
                continue;
            }

            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("campusId", campusId.toString());
            scopedRows.add(copy);
        }

        VoterService.ImportResult result = voterService.importVoters(scopedRows, request);

        List<String> combinedErrors = new ArrayList<>(skipErrors);
        combinedErrors.addAll(result.errors());

        return new VoterService.ImportResult(
                result.added(),
                result.updated(),
                result.skipped() + skippedForCampusMismatch,
                combinedErrors
        );
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