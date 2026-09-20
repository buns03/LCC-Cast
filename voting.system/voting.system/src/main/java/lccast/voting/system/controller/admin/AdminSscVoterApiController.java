package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.service.admin.AdminSscVoterService;
import lccast.voting.system.service.superadmin.VoterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-ssc/api/voters")
public class AdminSscVoterApiController {

    private final AdminSscVoterService voterService;

    public AdminSscVoterApiController(AdminSscVoterService voterService) {
        this.voterService = voterService;
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
}