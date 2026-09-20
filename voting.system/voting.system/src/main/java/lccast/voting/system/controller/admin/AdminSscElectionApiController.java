package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.superadmin.ElectionCreateRequest;
import lccast.voting.system.dto.superadmin.ElectionResponse;
import lccast.voting.system.service.admin.AdminSscElectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-ssc/api/elections")
public class AdminSscElectionApiController {

    private final AdminSscElectionService electionService;

    public AdminSscElectionApiController(AdminSscElectionService electionService) {
        this.electionService = electionService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    private UUID requireCampusId(HttpSession session) {
        Object campusId = session.getAttribute("campusId");
        if (campusId == null) throw new RuntimeException("No campus is associated with this account.");
        return campusId instanceof UUID ? (UUID) campusId : UUID.fromString(campusId.toString());
    }

    private UUID requireUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) throw new RuntimeException("Not authenticated.");
        return userId instanceof UUID ? (UUID) userId : UUID.fromString(userId.toString());
    }

    @GetMapping
    public ResponseEntity<List<ElectionResponse>> getElections(HttpSession session) {
        return ResponseEntity.ok(electionService.getActiveForCampus(requireCampusId(session)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ElectionResponse> getElection(@PathVariable UUID id, HttpSession session) {
        return ResponseEntity.ok(electionService.getById(id, requireCampusId(session)));
    }

    @PostMapping
    public ResponseEntity<ElectionResponse> createElection(
            @RequestBody ElectionCreateRequest request, HttpSession session, HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(electionService.create(
                request.getTitle(),
                requireCampusId(session),
                request.getSchoolYear(),
                request.getStartAt(),
                request.getEndAt(),
                requireUserId(session),
                request.getPartylistIds(),
                httpRequest
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ElectionResponse> updateElection(
            @PathVariable UUID id, @RequestBody ElectionCreateRequest request,
            HttpSession session, HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(electionService.update(
                id,
                request.getTitle(),
                requireCampusId(session),
                request.getSchoolYear(),
                request.getStartAt(),
                request.getEndAt(),
                request.getPartylistIds(),
                httpRequest
        ));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        electionService.archive(id, requireCampusId(session), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        electionService.delete(id, requireCampusId(session), request);
        return ResponseEntity.ok().build();
    }
}