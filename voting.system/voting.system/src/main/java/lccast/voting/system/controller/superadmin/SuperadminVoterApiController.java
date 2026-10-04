package lccast.voting.system.controller.superadmin;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;

import lccast.voting.system.service.superadmin.VoterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/superadmin/api/voters")
public class SuperadminVoterApiController {

    private final VoterRepository voterRepository;
    private final CampusRepository campusRepository;
    private final VoterService voterService;

    public SuperadminVoterApiController(
            VoterRepository voterRepository,
            CampusRepository campusRepository,
            VoterService voterService
    ) {
        this.voterRepository = voterRepository;
        this.campusRepository = campusRepository;
        this.voterService = voterService;
    }

// =========================================================
// GET ALL ACTIVE VOTERS (batched — fast)
// =========================================================

    @GetMapping
    public ResponseEntity<?> getVoters() {

        List<Voter> voters = voterRepository.findByStatus(RecordStatus.ACTIVE);

        return ResponseEntity.ok(voterService.toResponseList(voters));
    }

    @GetMapping("/page")
    public ResponseEntity<?> getVotersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID campusId,
            @RequestParam(required = false) String program,
            @RequestParam(required = false) String yearLevel,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sscStatus,
            @RequestParam(required = false) String departmentStatus,
            @RequestParam(defaultValue = "default") String nameSort,
            @RequestParam(defaultValue = "default") String timeSort
    ) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);

        Boolean sscVoted = toVotedFlag(sscStatus);
        Boolean deptVoted = toVotedFlag(departmentStatus);

        List<UUID> sscVotedIds = voterService.safeIdList(
                sscVoted == null ? Set.of() : voterService.resolveSscVotedVoterIds(campusId));
        List<UUID> deptVotedIds = voterService.safeIdList(
                deptVoted == null ? Set.of() : voterService.resolveDepartmentVotedVoterIds(
                        campusId, program == null ? null : List.of(program)));

        var result = voterRepository.searchWithVotingStatus(
                RecordStatus.ACTIVE, campusId, blankToNull(program), blankToNull(yearLevel),
                blankToNull(section), blankToNull(search == null ? null : search.toLowerCase()),
                sscVoted, sscVotedIds, deptVoted, deptVotedIds,
                voterService.normalizeNameSort(nameSort), voterService.normalizeTimeSort(timeSort),
                true, true, ElectionCategory.SSC,
                pageable
        );

        return ResponseEntity.ok(Map.of(
                "content", voterService.toResponseList(result.getContent()),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page
        ));
    }

    private Boolean toVotedFlag(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toLowerCase();
        if (s.contains("not")) return Boolean.FALSE;
        if (s.contains("voted")) return Boolean.TRUE;
        return null;
    }

    @GetMapping("/facets")
    public ResponseEntity<?> getVoterFacets(
            @RequestParam(required = false) UUID campusId,
            @RequestParam(required = false) List<String> program
    ) {
        List<String> sections;
        if (program == null || program.isEmpty()) {
            sections = voterRepository.findDistinctSections(RecordStatus.ACTIVE, campusId, (String) null);
        } else if (program.size() == 1) {
            sections = voterRepository.findDistinctSections(RecordStatus.ACTIVE, campusId, program.get(0));
        } else {
            sections = voterRepository.findDistinctSectionsForPrograms(RecordStatus.ACTIVE, campusId, program);
        }
        return ResponseEntity.ok(Map.of(
                "programs", voterRepository.findDistinctPrograms(RecordStatus.ACTIVE, campusId),
                "sections", sections
        ));
    }

    public record VoterBulkFilter(UUID campusId, String program, String yearLevel, String section, String search) {}

    @PatchMapping("/archive-matching")
    public ResponseEntity<?> archiveAllMatching(@RequestBody VoterBulkFilter filter, HttpServletRequest request) {
        List<UUID> ids = voterRepository.findIdsMatching(
                RecordStatus.ACTIVE, filter.campusId(), blankToNull(filter.program()),
                blankToNull(filter.yearLevel()), blankToNull(filter.section()), blankToNull(filter.search())
        );
        if (ids.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No matching voters.", "count", 0));
        }
        int count = voterService.archiveVoters(ids, request);
        return ResponseEntity.ok(Map.of("message", "Voters archived successfully.", "count", count));
    }

    @DeleteMapping("/delete-matching")
    public ResponseEntity<?> deleteAllMatching(@RequestBody VoterBulkFilter filter, HttpServletRequest request) {
        List<UUID> ids = voterRepository.findIdsMatching(
                RecordStatus.ACTIVE, filter.campusId(), blankToNull(filter.program()),
                blankToNull(filter.yearLevel()), blankToNull(filter.section()), blankToNull(filter.search())
        );
        if (ids.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No matching voters.", "count", 0));
        }
        int count = voterService.deleteVoters(ids, request);
        return ResponseEntity.ok(Map.of("message", "Voters moved to trash successfully.", "count", count));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

// =========================================================
// GET VOTER BY STUDENT ID
// =========================================================

    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getVoterByStudentId(
            @PathVariable String studentId
    ) {

        String trimmedStudentId = studentId.trim();

        if (trimmedStudentId.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        return voterRepository
                .findByStudentId(trimmedStudentId)
                .filter(voter -> voter.getStatus() == RecordStatus.ACTIVE)
                .map(voter -> ResponseEntity.ok(voterService.toResponse(voter)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


// =========================================================
// GET SINGLE VOTER
// =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<?> getVoter(
            @PathVariable UUID id
    ) {

        return voterRepository
                .findByIdAndStatus(id, RecordStatus.ACTIVE)
                .map(voter -> ResponseEntity.ok(voterService.toResponse(voter)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

// =========================================================
// UPDATE VOTER
// =========================================================

    @PutMapping("/{id}")
    public ResponseEntity<?> updateVoter(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest
    ){

        Voter voter = voterRepository
                .findByIdAndStatus(id, RecordStatus.ACTIVE)
                .orElse(null);

        if (voter == null) {
            return ResponseEntity.notFound().build();
        }

        String studentId = stringValue(request.get("studentId"));

        if (studentId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Student ID is required.")
            );
        }

        Optional<Voter> existingVoter =
                voterRepository.findByStudentId(studentId);

        if (existingVoter.isPresent()
                && !existingVoter.get().getId().equals(id)) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "This Student ID is already registered."
                    )
            );
        }

        voter.setStudentId(studentId);
        voter.setLastName(stringValue(request.get("lastName")));
        voter.setFirstName(stringValue(request.get("firstName")));
        voter.setMiddleName(stringValue(request.get("middleName")));
        voter.setFullName(stringValue(request.get("fullName")));
        voter.setEmail(stringValue(request.get("email")));
        voter.setProgramCourse(stringValue(request.get("programCourse")));
        voter.setYearLevel(stringValue(request.get("yearLevel")));
        voter.setSection(stringValue(request.get("section")));

        String campusId = stringValue(request.get("campusId"));

        if (campusId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Campus is required.")
            );
        }

        UUID campusUUID;

        try {
            campusUUID = UUID.fromString(campusId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Invalid campus ID.")
            );
        }

        if (!campusRepository.existsById(campusUUID)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Selected campus was not found.")
            );
        }

        voter.setCampusId(campusUUID);

        Voter saved = voterRepository.save(voter);

        voterService.auditUpdate(
                httpRequest,
                saved
        );

        return ResponseEntity.ok(voterService.toResponse(saved));
    }


// =========================================================
// ARCHIVE VOTER
// =========================================================

    @PatchMapping("/{id}/archive")
    public ResponseEntity<?> archiveVoter(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        try {
            voterService.archiveVoter(id, request);

            return ResponseEntity.ok(
                    Map.of("message", "Voter archived successfully.")
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }


// =========================================================
// MOVE VOTER TO TRASH
// =========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVoter(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        try {
            voterService.deleteVoter(id, request);

            return ResponseEntity.ok(
                    Map.of("message", "Voter moved to trash successfully.")
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

// =========================================================
// BULK ARCHIVE VOTERS
// =========================================================

    @PatchMapping("/archive")
    public ResponseEntity<?> archiveVoters(
            @RequestBody List<UUID> ids,
            HttpServletRequest request
    ) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "No voters selected.")
                );
            }

            int count = voterService.archiveVoters(ids, request);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Voters archived successfully.",
                            "count", count
                    )
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

// =========================================================
// BULK DELETE / TRASH VOTERS
// =========================================================

    @DeleteMapping("/bulk")
    public ResponseEntity<?> deleteVoters(
            @RequestBody List<UUID> ids,
            HttpServletRequest request
    ) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "No voters selected.")
                );
            }

            int count = voterService.deleteVoters(ids, request);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Voters moved to trash successfully.",
                            "count", count
                    )
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

// =========================================================
// RESTORE VOTER FROM TRASH
// =========================================================

    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreVoter(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {

        Voter voter = voterRepository
                .findById(id)
                .orElse(null);

        if (voter == null) {
            return ResponseEntity.notFound().build();
        }

        if (voter.getStatus() != RecordStatus.DELETED
                && voter.getStatus() != RecordStatus.ARCHIVED) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Only deleted or archived voters can be restored.")
            );
        }

        voter.setStatus(RecordStatus.ACTIVE);

        Voter restored = voterRepository.save(voter);

        voterService.restoreVoterRecords(restored.getId());

        voterService.auditRestore(
                request,
                restored
        );

        return ResponseEntity.ok(voterService.toResponse(restored));
    }

// =========================================================
// PERMANENTLY DELETE VOTER
// =========================================================

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<?> permanentlyDeleteVoter(
            @PathVariable UUID id
    ) {
        try {
            voterService.permanentlyDeleteVoter(id);

            return ResponseEntity.ok(
                    Map.of("message", "Voter permanently deleted.")
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

// =========================================================
// STRING HELPER
// =========================================================

    private String stringValue(Object value) {
        return value == null
                ? ""
                : String.valueOf(value).trim();
    }

    @PostMapping("/import")
    public ResponseEntity<?> importVoters(
            @RequestBody List<Map<String, Object>> rows,
            HttpServletRequest request
    ){

        if (rows == null || rows.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "The import file contains no voter records."
                    )
            );
        }

        VoterService.ImportResult result =
                voterService.importVoters(rows, request);

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "added", result.added(),
                        "updated", result.updated(),
                        "skipped", result.skipped(),
                        "errors", result.errors()
                )
        );
    }

    @GetMapping("/export")
    public void exportVoters(
            @RequestParam(required = false) UUID campusId,
            @RequestParam(required = false) String program,
            @RequestParam(required = false) String yearLevel,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String search,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        List<UUID> ids = voterRepository.findIdsMatching(
                RecordStatus.ACTIVE, campusId, blankToNull(program), blankToNull(yearLevel),
                blankToNull(section), blankToNull(search)
        );
        List<Map<String, Object>> rows = ids.isEmpty()
                ? List.of()
                : voterService.toResponseList(voterRepository.findAllById(ids));

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"lccast-voters.csv\"");

        var writer = response.getWriter();
        writer.write("Student ID,Full Name,Course,Year Level,Section,Campus,Email,SSC Voting Status,SSC Time Voted,Department Voting Status,Department Time Voted\n");

        java.util.function.Function<Object, String> esc = val ->
                "\"" + String.valueOf(val == null ? "" : val).replace("\"", "\"\"") + "\"";

        for (Map<String, Object> v : rows) {
            writer.write(esc.apply(v.get("studentId")) + "," +
                    esc.apply(v.get("fullName")) + "," +
                    esc.apply(v.get("programCourse")) + "," +
                    esc.apply(v.get("yearLevel")) + "," +
                    esc.apply(v.get("section")) + "," +
                    esc.apply(v.get("campus")) + "," +
                    esc.apply(v.get("email")) + "," +
                    esc.apply(v.get("sscVotingStatus")) + "," +
                    esc.apply(v.get("sscVotedAt")) + "," +
                    esc.apply(v.get("departmentVotingStatus")) + "," +
                    esc.apply(v.get("departmentVotedAt")) + "\n");
        }
        writer.flush();
    }

}