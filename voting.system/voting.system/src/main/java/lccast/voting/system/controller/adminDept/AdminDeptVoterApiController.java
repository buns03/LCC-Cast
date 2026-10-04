package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.student.VoterDepartmentElectionService;
import lccast.voting.system.service.superadmin.VoterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin-dept/api/voters")
public class AdminDeptVoterApiController {

    private final VoterRepository voterRepository;
    private final CampusRepository campusRepository;
    private final VoterService voterService;
    private final ElectionRepository electionRepository;
    private final VoteLogRepository voteLogRepository;
    private final VoterDepartmentElectionService voterDepartmentElectionService;

    public AdminDeptVoterApiController(
            VoterRepository voterRepository,
            CampusRepository campusRepository,
            VoterService voterService,
            ElectionRepository electionRepository,
            VoteLogRepository voteLogRepository,
            VoterDepartmentElectionService voterDepartmentElectionService
    ) {
        this.voterRepository = voterRepository;
        this.campusRepository = campusRepository;
        this.voterService = voterService;
        this.electionRepository = electionRepository;
        this.voteLogRepository = voteLogRepository;
        this.voterDepartmentElectionService = voterDepartmentElectionService;
    }

    // =====================================================
    // SCOPE HELPER — session is the ONLY source of truth
    // =====================================================

    private record Scope(UUID campusId, String programCourse) {}

    private Scope requireScope(HttpSession session) {
        UUID campusId = (UUID) session.getAttribute("campusId");
        String programCourse = (String) session.getAttribute("programCourse");

        if (campusId == null || programCourse == null || programCourse.isBlank()) {
            throw new IllegalStateException(
                    "Missing campusId/programCourse in session — admin-dept user not properly scoped."
            );
        }
        return new Scope(campusId, programCourse);
    }

    // =====================================================
    // GET ALL — scoped to this admin's own campus + program
    // =====================================================

    @GetMapping
    public ResponseEntity<?> getVoters(HttpSession session) {

        Scope scope = requireScope(session);

        List<Voter> voters = voterRepository.findByCampusIdAndProgramCourseAndStatus(
                scope.campusId(), scope.programCourse(), RecordStatus.ACTIVE);

        if (voters.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        String campusName = campusRepository.findById(scope.campusId())
                .map(Campus::getName)
                .orElse("Unknown");
        Map<UUID, String> campusNamesById = Map.of(scope.campusId(), campusName);

        Map<UUID, UUID> departmentElectionIdByVoter = new HashMap<>();
        for (Voter voter : voters) {
            voterDepartmentElectionService.resolveElectionForStatus(voter)
                    .ifPresent(election -> departmentElectionIdByVoter.put(voter.getId(), election.getId()));
        }

        Set<UUID> relevantElectionIds = new HashSet<>(departmentElectionIdByVoter.values());

        Map<String, VoteLog> voteLogByElectionAndVoter = relevantElectionIds.isEmpty()
                ? Map.of()
                : voteLogRepository.findByElectionIdIn(relevantElectionIds).stream()
                .collect(Collectors.toMap(
                        vl -> vl.getElectionId() + "|" + vl.getVoterId(), vl -> vl, (a, b) -> a));

        List<Map<String, Object>> response = voters.stream()
                .map(v -> buildVoterResponse(v, departmentElectionIdByVoter, voteLogByElectionAndVoter))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET ONE — 404s (not 403) if outside this admin's scope,
    // so scope is never confirmed to an attacker via status code
    // =====================================================

    @GetMapping("/{id}")
    public ResponseEntity<?> getVoter(@PathVariable UUID id, HttpSession session) {
        Scope scope = requireScope(session);

        return voterRepository.findByIdAndCampusIdAndProgramCourseAndStatus(
                        id, scope.campusId(), scope.programCourse(), RecordStatus.ACTIVE)
                .map(voter -> ResponseEntity.ok(toResponse(voter, scope)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // =====================================================
    // UPDATE — campusId/programCourse are NEVER taken from the
    // request body. An admin-dept user cannot move a voter out
    // of (or fabricate one into) their own scope this way.
    // =====================================================

    @PutMapping("/{id}")
    public ResponseEntity<?> updateVoter(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request,
            HttpSession session,
            HttpServletRequest httpRequest
    ) {
        Scope scope = requireScope(session);

        Voter voter = voterRepository.findByIdAndCampusIdAndProgramCourseAndStatus(
                        id, scope.campusId(), scope.programCourse(), RecordStatus.ACTIVE)
                .orElse(null);

        if (voter == null) {
            return ResponseEntity.notFound().build();
        }

        String studentId = stringValue(request.get("studentId"));
        if (studentId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Student ID is required."));
        }

        Optional<Voter> existingVoter = voterRepository.findByStudentId(studentId);
        if (existingVoter.isPresent() && !existingVoter.get().getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "This Student ID is already registered."));
        }

        voter.setStudentId(studentId);
        voter.setLastName(stringValue(request.get("lastName")));
        voter.setFirstName(stringValue(request.get("firstName")));
        voter.setMiddleName(stringValue(request.get("middleName")));
        voter.setFullName(stringValue(request.get("fullName")));
        voter.setEmail(stringValue(request.get("email")));
        voter.setYearLevel(stringValue(request.get("yearLevel")));
        voter.setSection(stringValue(request.get("section")));
        // campusId and programCourse intentionally left untouched — fixed to this admin's scope

        Voter saved = voterRepository.save(voter);
        voterService.auditUpdate(httpRequest, saved);

        return ResponseEntity.ok(toResponse(saved, scope));
    }

    // =====================================================
    // ARCHIVE / DELETE / RESTORE — ownership verified first,
    // then the existing VoterService logic (campus-agnostic —
    // it just flips status on the row it's given) is reused.
    // =====================================================

    @PatchMapping("/{id}/archive")
    public ResponseEntity<?> archiveVoter(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);
        if (voterRepository.findByIdAndCampusIdAndProgramCourseAndStatus(
                id, scope.campusId(), scope.programCourse(), RecordStatus.ACTIVE).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            voterService.archiveVoter(id, request);
            return ResponseEntity.ok(Map.of("message", "Voter archived successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVoter(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);
        if (voterRepository.findByIdAndCampusIdAndProgramCourseAndStatus(
                id, scope.campusId(), scope.programCourse(), RecordStatus.ACTIVE).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            voterService.deleteVoter(id, request);
            return ResponseEntity.ok(Map.of("message", "Voter moved to trash successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/archive")
    public ResponseEntity<?> archiveVoters(@RequestBody List<UUID> ids, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);
        List<UUID> owned = filterToScope(ids, scope);
        if (owned.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "No voters selected."));
        int count = voterService.archiveVoters(owned, request);
        return ResponseEntity.ok(Map.of("message", "Voters archived successfully.", "count", count));
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<?> deleteVoters(@RequestBody List<UUID> ids, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);
        List<UUID> owned = filterToScope(ids, scope);
        if (owned.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "No voters selected."));
        int count = voterService.deleteVoters(owned, request);
        return ResponseEntity.ok(Map.of("message", "Voters moved to trash successfully.", "count", count));
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreVoter(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);

        Voter voter = voterRepository.findById(id).orElse(null);
        if (voter == null
                || !scope.campusId().equals(voter.getCampusId())
                || voter.getProgramCourse() == null
                || !voter.getProgramCourse().trim().equalsIgnoreCase(scope.programCourse().trim())) {
            return ResponseEntity.notFound().build();
        }

        if (voter.getStatus() != RecordStatus.DELETED && voter.getStatus() != RecordStatus.ARCHIVED) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only deleted or archived voters can be restored."));
        }

        voter.setStatus(RecordStatus.ACTIVE);
        Voter restored = voterRepository.save(voter);
        voterService.restoreVoterRecords(restored.getId());
        voterService.auditRestore(request, restored);

        return ResponseEntity.ok(toResponse(restored, scope));
    }

    // Note: permanent-delete is intentionally NOT exposed here — that's
    // an irreversible action left to superadmin only.

    // =====================================================
    // IMPORT — campusId/programCourse are forced to this admin's
    // scope regardless of what the uploaded file contains.
    // =====================================================

    @PostMapping("/import")
    public ResponseEntity<?> importVoters(@RequestBody List<Map<String, Object>> rows, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);

        if (rows == null || rows.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "The import file contains no voter records."));
        }

        String campusName = campusRepository.findById(scope.campusId())
                .map(Campus::getName)
                .orElse(null);

        List<Map<String, Object>> scopedRows = new ArrayList<>();
        List<String> skipErrors = new ArrayList<>();
        int skippedForScopeMismatch = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            int rowNumber = i + 2;

            String rowCampus = stringValue(row.get("campus"));
            String rowProgram = stringValue(row.get("programCourse"));

            if (!rowCampus.isBlank() && campusName != null && !rowCampus.equalsIgnoreCase(campusName.trim())) {
                skipErrors.add("Row " + rowNumber + ": Skipped — student belongs to campus \"" + rowCampus
                        + "\", not your campus (\"" + campusName + "\").");
                skippedForScopeMismatch++;
                continue;
            }

            if (!rowProgram.isBlank() && !rowProgram.equalsIgnoreCase(scope.programCourse().trim())) {
                skipErrors.add("Row " + rowNumber + ": Skipped — student belongs to course \"" + rowProgram
                        + "\", not your department (\"" + scope.programCourse() + "\").");
                skippedForScopeMismatch++;
                continue;
            }

            Map<String, Object> copy = new HashMap<>(row);
            copy.put("campusId", scope.campusId().toString());
            copy.put("programCourse", scope.programCourse());
            scopedRows.add(copy);
        }

        VoterService.ImportResult result = voterService.importVoters(scopedRows, request);

        List<String> combinedErrors = new ArrayList<>(skipErrors);
        combinedErrors.addAll(result.errors());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "added", result.added(),
                "updated", result.updated(),
                "skipped", result.skipped() + skippedForScopeMismatch,
                "errors", combinedErrors
        ));
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private List<UUID> filterToScope(List<UUID> ids, Scope scope) {
        if (ids == null || ids.isEmpty()) return List.of();
        Set<UUID> owned = voterRepository
                .findByCampusIdAndProgramCourseAndStatus(scope.campusId(), scope.programCourse(), RecordStatus.ACTIVE)
                .stream().map(Voter::getId).collect(Collectors.toSet());
        return ids.stream().filter(owned::contains).toList();
    }

    private Map<String, Object> buildVoterResponse(
            Voter voter, Map<UUID, UUID> departmentElectionIdByVoter,
            Map<String, VoteLog> voteLogByElectionAndVoter
    ) {
        String campusName = campusRepository.findById(voter.getCampusId()).map(Campus::getName).orElse("Unknown");
        Map<String, Object> response = new HashMap<>();
        response.put("id", voter.getId());
        response.put("studentId", voter.getStudentId());
        response.put("lastName", voter.getLastName());
        response.put("firstName", voter.getFirstName());
        response.put("createdAt", voter.getCreatedAt());
        response.put("updatedAt", voter.getUpdatedAt());
        response.put("middleName", voter.getMiddleName() == null ? "" : voter.getMiddleName());
        response.put("fullName", voter.getFullName());
        response.put("email", voter.getEmail() == null ? "" : voter.getEmail());
        response.put("programCourse", voter.getProgramCourse() == null ? "" : voter.getProgramCourse());
        response.put("yearLevel", voter.getYearLevel() == null ? "" : voter.getYearLevel());
        response.put("section", voter.getSection() == null ? "" : voter.getSection());
        response.put("campusId", voter.getCampusId());
        response.put("campus", campusName);

        UUID departmentElectionId = departmentElectionIdByVoter.get(voter.getId());
        VoteLog departmentVoteLog = departmentElectionId != null
                ? voteLogByElectionAndVoter.get(departmentElectionId + "|" + voter.getId()) : null;
        response.put("departmentVotingStatus", departmentVoteLog != null ? "VOTED" : "NOT_VOTED");
        response.put("departmentVotedAt", departmentVoteLog != null ? departmentVoteLog.getVotedAt() : null);

        response.put("status", voter.getStatus().name());
        return response;
    }

    private Map<String, Object> toResponse(Voter voter, Scope scope) {

        Map<UUID, UUID> departmentElectionIdByVoter = new HashMap<>();
        voterDepartmentElectionService.resolveElectionForStatus(voter)
                .ifPresent(election -> departmentElectionIdByVoter.put(voter.getId(), election.getId()));

        Set<UUID> relevantElectionIds = new HashSet<>(departmentElectionIdByVoter.values());

        Map<String, VoteLog> voteLogByElectionAndVoter = relevantElectionIds.isEmpty()
                ? Map.of()
                : voteLogRepository.findByElectionIdIn(relevantElectionIds).stream()
                .collect(Collectors.toMap(
                        vl -> vl.getElectionId() + "|" + vl.getVoterId(), vl -> vl, (a, b) -> a));

        return buildVoterResponse(voter, departmentElectionIdByVoter, voteLogByElectionAndVoter);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    // =====================================================
    // PAGINATED READ
    // =====================================================

    @GetMapping("/page")
    public ResponseEntity<?> getVotersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String yearLevel,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String departmentStatus,
            @RequestParam(defaultValue = "default") String nameSort,
            @RequestParam(defaultValue = "default") String timeSort,
            HttpSession session
    ) {
        Scope scope = requireScope(session);
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);

        Boolean deptVoted = toVotedFlag(departmentStatus);
        List<UUID> deptVotedIds = voterService.safeIdList(
                deptVoted == null ? Set.of() : voterService.resolveDepartmentVotedVoterIds(
                        scope.campusId(), List.of(scope.programCourse())));

        var result = voterRepository.searchByCampusAndProgramWithVotingStatus(
                RecordStatus.ACTIVE, scope.campusId(), scope.programCourse(),
                blankToNull(yearLevel), blankToNull(section),
                blankToNull(search == null ? null : search.toLowerCase()),
                deptVoted, deptVotedIds,
                voterService.normalizeNameSort(nameSort), voterService.normalizeTimeSort(timeSort),
                ElectionCategory.SSC,
                pageable
        );

        List<Voter> voters = result.getContent();
        Map<String, Object> body;

        if (voters.isEmpty()) {
            body = Map.of("content", List.of(), "totalElements", 0L, "totalPages", 0, "page", page);
        } else {
            Map<UUID, UUID> departmentElectionIdByVoter = new HashMap<>();
            Map<String, Optional<Election>> electionCache = new HashMap<>();
            for (Voter voter : voters) {
                String key = voter.getCampusId() + "|" + voter.getProgramCourse();
                Optional<Election> election = electionCache.computeIfAbsent(
                        key, k -> voterDepartmentElectionService.resolveElectionForStatus(voter));
                election.ifPresent(e -> departmentElectionIdByVoter.put(voter.getId(), e.getId()));
            }

            Set<UUID> relevantElectionIds = new HashSet<>(departmentElectionIdByVoter.values());
            Map<String, VoteLog> voteLogByElectionAndVoter = relevantElectionIds.isEmpty()
                    ? Map.of()
                    : voteLogRepository.findByElectionIdIn(relevantElectionIds).stream()
                    .collect(Collectors.toMap(
                            vl -> vl.getElectionId() + "|" + vl.getVoterId(), vl -> vl, (a, b) -> a));

            List<Map<String, Object>> content = voters.stream()
                    .map(v -> buildVoterResponse(v, departmentElectionIdByVoter, voteLogByElectionAndVoter))
                    .collect(Collectors.toList());

            body = Map.of(
                    "content", content,
                    "totalElements", result.getTotalElements(),
                    "totalPages", result.getTotalPages(),
                    "page", page
            );
        }

        return ResponseEntity.ok(body);
    }

    private Boolean toVotedFlag(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toLowerCase();
        if (s.contains("not")) return Boolean.FALSE;
        if (s.contains("voted")) return Boolean.TRUE;
        return null;
    }

    // =====================================================
    // FACETS
    // =====================================================

    @GetMapping("/facets")
    public ResponseEntity<?> getVoterFacets(HttpSession session) {
        Scope scope = requireScope(session);
        return ResponseEntity.ok(Map.of(
                "sections", voterRepository.findDistinctSectionsForDepartment(RecordStatus.ACTIVE, scope.campusId(), scope.programCourse()),
                "yearLevels", voterRepository.findDistinctYearLevelsForDepartment(RecordStatus.ACTIVE, scope.campusId(), scope.programCourse())
        ));
    }

    // =====================================================
    // BULK BY FILTER
    // =====================================================

    public record VoterBulkFilter(String yearLevel, String section, String search) {}

    @PatchMapping("/archive-matching")
    public ResponseEntity<?> archiveAllMatching(
            @RequestBody VoterBulkFilter filter, HttpSession session, HttpServletRequest request
    ) {
        Scope scope = requireScope(session);
        List<UUID> ids = voterRepository.findIdsMatchingByCampusAndProgram(
                RecordStatus.ACTIVE, scope.campusId(), scope.programCourse(),
                blankToNull(filter.yearLevel()), blankToNull(filter.section()), blankToNull(filter.search())
        );
        if (ids.isEmpty()) return ResponseEntity.ok(Map.of("message", "No matching voters.", "count", 0));
        int count = voterService.archiveVoters(ids, request);
        return ResponseEntity.ok(Map.of("message", "Voters archived successfully.", "count", count));
    }

    @DeleteMapping("/delete-matching")
    public ResponseEntity<?> deleteAllMatching(
            @RequestBody VoterBulkFilter filter, HttpSession session, HttpServletRequest request
    ) {
        Scope scope = requireScope(session);
        List<UUID> ids = voterRepository.findIdsMatchingByCampusAndProgram(
                RecordStatus.ACTIVE, scope.campusId(), scope.programCourse(),
                blankToNull(filter.yearLevel()), blankToNull(filter.section()), blankToNull(filter.search())
        );
        if (ids.isEmpty()) return ResponseEntity.ok(Map.of("message", "No matching voters.", "count", 0));
        int count = voterService.deleteVoters(ids, request);
        return ResponseEntity.ok(Map.of("message", "Voters moved to trash successfully.", "count", count));
    }

    // =====================================================
    // EXPORT
    // =====================================================

    @GetMapping("/export")
    public void exportVoters(
            @RequestParam(required = false) String yearLevel,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String search,
            HttpSession session,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        Scope scope = requireScope(session);
        List<UUID> ids = voterRepository.findIdsMatchingByCampusAndProgram(
                RecordStatus.ACTIVE, scope.campusId(), scope.programCourse(),
                blankToNull(yearLevel), blankToNull(section), blankToNull(search)
        );

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"lccast-voters.csv\"");
        var writer = response.getWriter();
        writer.write("Student ID,Full Name,Year Level,Section,Campus,Email,Department Voting Status,Department Time Voted\n");

        if (!ids.isEmpty()) {
            List<Voter> voters = voterRepository.findAllById(ids);
            Map<UUID, UUID> departmentElectionIdByVoter = new HashMap<>();
            for (Voter voter : voters) {
                voterDepartmentElectionService.resolveElectionForStatus(voter)
                        .ifPresent(e -> departmentElectionIdByVoter.put(voter.getId(), e.getId()));
            }
            Set<UUID> relevantElectionIds = new HashSet<>(departmentElectionIdByVoter.values());
            Map<String, VoteLog> voteLogByElectionAndVoter = relevantElectionIds.isEmpty()
                    ? Map.of()
                    : voteLogRepository.findByElectionIdIn(relevantElectionIds).stream()
                    .collect(Collectors.toMap(vl -> vl.getElectionId() + "|" + vl.getVoterId(), vl -> vl, (a, b) -> a));

            java.util.function.Function<Object, String> esc = val ->
                    "\"" + String.valueOf(val == null ? "" : val).replace("\"", "\"\"") + "\"";

            for (Voter v : voters) {
                Map<String, Object> row = buildVoterResponse(v, departmentElectionIdByVoter, voteLogByElectionAndVoter);
                writer.write(esc.apply(row.get("studentId")) + "," +
                        esc.apply(row.get("fullName")) + "," +
                        esc.apply(row.get("yearLevel")) + "," +
                        esc.apply(row.get("section")) + "," +
                        esc.apply(row.get("campus")) + "," +
                        esc.apply(row.get("email")) + "," +
                        esc.apply(row.get("departmentVotingStatus")) + "," +
                        esc.apply(row.get("departmentVotedAt")) + "\n");
            }
        }
        writer.flush();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}