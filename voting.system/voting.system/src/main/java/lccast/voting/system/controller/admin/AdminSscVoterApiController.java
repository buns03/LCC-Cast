package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.service.admin.AdminSscVoterService;
import lccast.voting.system.service.superadmin.VoterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lccast.voting.system.model.ElectionCategory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-ssc/api/voters")
public class AdminSscVoterApiController {

    private final AdminSscVoterService voterService;
    private final VoterRepository voterRepository;

    public AdminSscVoterApiController(AdminSscVoterService voterService,
                                      VoterRepository voterRepository) {
        this.voterService = voterService;
        this.voterRepository = voterRepository;
    }

    private UUID requireCampusId(HttpSession session) {
        Object campusId = session.getAttribute("campusId");
        if (campusId == null) throw new RuntimeException("No campus is associated with this account.");
        return campusId instanceof UUID ? (UUID) campusId : UUID.fromString(campusId.toString());
    }

    @GetMapping
    public ResponseEntity<?> getVoters(HttpSession session) {
        return ResponseEntity.ok(voterService.getActiveForCampus(requireCampusId(session)));
    }

    // AdminSscVoterApiController — add the param and thread it through
    @GetMapping("/page")
    public ResponseEntity<?> getVotersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String program,
            @RequestParam(required = false) String yearLevel,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sscStatus,
            @RequestParam(defaultValue = "default") String nameSort,
            @RequestParam(defaultValue = "default") String timeSort,
            HttpSession session
    ) {
        return ResponseEntity.ok(
                voterService.getPageForCampus(
                        requireCampusId(session), page, size, program, yearLevel, section, search, sscStatus,
                        nameSort, timeSort
                )
        );
    }

    private Boolean toVotedFlag(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toLowerCase().replace("_", " ").replace("-", " ").trim();
        if (s.equals("voted")) return Boolean.TRUE;
        if (s.equals("not voted")) return Boolean.FALSE;
        return null;
    }

    @GetMapping("/facets")
    public ResponseEntity<?> getVoterFacets(@RequestParam(required = false) List<String> program, HttpSession session) {
        UUID campusId = requireCampusId(session);
        return ResponseEntity.ok(Map.of(
                "programs", voterService.getProgramFacets(campusId),
                "sections", voterService.getSectionFacets(campusId, program)
        ));
    }

    public record VoterBulkFilter(String program, String yearLevel, String section, String search) {}

    @PatchMapping("/archive-matching")
    public ResponseEntity<?> archiveAllMatching(
            @RequestBody VoterBulkFilter filter, HttpSession session, HttpServletRequest request
    ) {
        int count = voterService.archiveAllMatching(
                requireCampusId(session), filter.program(), filter.yearLevel(),
                filter.section(), filter.search(), request
        );
        return ResponseEntity.ok(Map.of("message", "Voters archived successfully.", "count", count));
    }

    @DeleteMapping("/delete-matching")
    public ResponseEntity<?> deleteAllMatching(
            @RequestBody VoterBulkFilter filter, HttpSession session, HttpServletRequest request
    ) {
        int count = voterService.deleteAllMatching(
                requireCampusId(session), filter.program(), filter.yearLevel(),
                filter.section(), filter.search(), request
        );
        return ResponseEntity.ok(Map.of("message", "Voters moved to trash successfully.", "count", count));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getByStudentId(@PathVariable String studentId, HttpSession session) {
        return voterService.getByStudentId(studentId, requireCampusId(session))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getVoter(@PathVariable UUID id, HttpSession session) {
        try {
            return ResponseEntity.ok(voterService.getById(id, requireCampusId(session)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateVoter(
            @PathVariable UUID id, @RequestBody Map<String, Object> request,
            HttpSession session, HttpServletRequest httpRequest
    ) {
        try {
            return ResponseEntity.ok(voterService.update(id, requireCampusId(session), request, httpRequest));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<?> archiveVoter(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        try {
            voterService.archive(id, requireCampusId(session), request);
            return ResponseEntity.ok(Map.of("message", "Voter archived successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVoter(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        try {
            voterService.delete(id, requireCampusId(session), request);
            return ResponseEntity.ok(Map.of("message", "Voter moved to trash successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreVoter(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        try {
            voterService.restore(id, requireCampusId(session), request);
            return ResponseEntity.ok(Map.of("message", "Voter restored successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/archive")
    public ResponseEntity<?> archiveVoters(@RequestBody List<UUID> ids, HttpSession session, HttpServletRequest request) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No voters selected."));
        }
        int count = voterService.archiveAllInCampus(requireCampusId(session), ids, request);
        return ResponseEntity.ok(Map.of("message", "Voters archived successfully.", "count", count));
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<?> deleteVoters(@RequestBody List<UUID> ids, HttpSession session, HttpServletRequest request) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No voters selected."));
        }
        int count = voterService.deleteAllInCampus(requireCampusId(session), ids, request);
        return ResponseEntity.ok(Map.of("message", "Voters moved to trash successfully.", "count", count));
    }

    @PostMapping("/import")
    public ResponseEntity<?> importVoters(
            @RequestBody List<Map<String, Object>> rows, HttpSession session, HttpServletRequest request
    ) {
        if (rows == null || rows.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "The import file contains no voter records."));
        }

        VoterService.ImportResult result = voterService.importForCampus(requireCampusId(session), rows, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "added", result.added(),
                "updated", result.updated(),
                "skipped", result.skipped(),
                "errors", result.errors()
        ));
    }

    @GetMapping("/export")
    public void exportVoters(
            @RequestParam(required = false) String program,
            @RequestParam(required = false) String yearLevel,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String search,
            HttpSession session,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        UUID campusId = requireCampusId(session);
        List<Map<String, Object>> rows = voterService.exportMatching(campusId, program, yearLevel, section, search);
        writeVotersCsv(response, rows, false); // false = no department columns for SSC
    }

    private void writeVotersCsv(
            jakarta.servlet.http.HttpServletResponse response,
            List<Map<String, Object>> rows,
            boolean includeDepartmentColumns
    ) throws java.io.IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"lccast-voters.csv\"");

        var writer = response.getWriter();
        writer.write("Student ID,Full Name,Course,Year Level,Section,Campus,Email,SSC Voting Status,SSC Time Voted");
        if (includeDepartmentColumns) writer.write(",Department Voting Status,Department Time Voted");
        writer.write("\n");

        for (Map<String, Object> v : rows) {
            writer.write(csvRow(v, includeDepartmentColumns));
        }
        writer.flush();
    }

    private String csvRow(Map<String, Object> v, boolean includeDepartmentColumns) {
        java.util.function.Function<Object, String> esc = val ->
                "\"" + String.valueOf(val == null ? "" : val).replace("\"", "\"\"") + "\"";

        StringBuilder sb = new StringBuilder();
        sb.append(esc.apply(v.get("studentId"))).append(",");
        sb.append(esc.apply(v.get("fullName"))).append(",");
        sb.append(esc.apply(v.get("programCourse"))).append(",");
        sb.append(esc.apply(v.get("yearLevel"))).append(",");
        sb.append(esc.apply(v.get("section"))).append(",");
        sb.append(esc.apply(v.get("campus"))).append(",");
        sb.append(esc.apply(v.get("email"))).append(",");
        sb.append(esc.apply(v.get("sscVotingStatus"))).append(",");
        sb.append(esc.apply(v.get("sscVotedAt")));
        if (includeDepartmentColumns) {
            sb.append(",").append(esc.apply(v.get("departmentVotingStatus")));
            sb.append(",").append(esc.apply(v.get("departmentVotedAt")));
        }
        sb.append("\n");
        return sb.toString();
    }
}