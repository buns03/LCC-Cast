package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.superadmin.ElectionCreateRequest;
import lccast.voting.system.dto.superadmin.ElectionResponse;
import lccast.voting.system.service.adminDept.AdminElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-dept/api/elections")
public class AdminDeptElectionApiController {

    private final AdminElectionService adminElectionService;

    public AdminDeptElectionApiController(AdminElectionService adminElectionService) {
        this.adminElectionService = adminElectionService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    private UUID sessionCampusId(HttpSession session) {
        Object raw = session.getAttribute("campusId");
        return raw == null ? null : UUID.fromString(raw.toString());
    }

    private String sessionDepartmentCode(HttpSession session) {
        Object raw = session.getAttribute("programCourse");
        return raw == null ? null : raw.toString().trim().toUpperCase();
    }

    private UUID sessionUserId(HttpSession session) {
        Object raw = session.getAttribute("userId");
        return raw == null ? null : UUID.fromString(raw.toString());
    }

    @GetMapping
    public ResponseEntity<List<ElectionResponse>> getElections(HttpSession session) {
        UUID campusId = sessionCampusId(session);
        String deptCode = sessionDepartmentCode(session);

        if (campusId == null || deptCode == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(adminElectionService.getScopedElections(campusId, deptCode));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ElectionResponse> getElection(
            @PathVariable UUID id,
            HttpSession session
    ) {
        UUID campusId = sessionCampusId(session);
        String deptCode = sessionDepartmentCode(session);

        return ResponseEntity.ok(adminElectionService.getScopedById(id, campusId, deptCode));
    }

    @PostMapping
    public ResponseEntity<?> createElection(
            @RequestBody ElectionCreateRequest request,
            HttpSession session,
            HttpServletRequest httpRequest
    ) {
        UUID createdBy = sessionUserId(session);
        UUID campusId = sessionCampusId(session);
        String deptCode = sessionDepartmentCode(session);

        if (createdBy == null || campusId == null || deptCode == null) {
            return ResponseEntity.status(401).build();
        }

        ElectionResponse response = adminElectionService.create(
                request.getTitle(),
                request.getCampusId(),
                request.getSchoolYear(),
                request.getStartAt(),
                request.getEndAt(),
                createdBy,
                request.getDepartmentIds(),
                campusId,
                deptCode,
                httpRequest
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateElection(
            @PathVariable UUID id,
            @RequestBody ElectionCreateRequest request,
            HttpSession session,
            HttpServletRequest httpRequest
    ) {
        UUID campusId = sessionCampusId(session);
        String deptCode = sessionDepartmentCode(session);

        if (campusId == null || deptCode == null) {
            return ResponseEntity.status(401).build();
        }

        ElectionResponse response = adminElectionService.update(
                id,
                request.getTitle(),
                request.getCampusId(),
                request.getSchoolYear(),
                request.getStartAt(),
                request.getEndAt(),
                request.getDepartmentIds(),
                campusId,
                deptCode,
                httpRequest
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Void> archiveElection(
            @PathVariable UUID id,
            HttpSession session,
            HttpServletRequest httpRequest
    ) {
        adminElectionService.archive(id, sessionCampusId(session), sessionDepartmentCode(session), httpRequest);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteElection(
            @PathVariable UUID id,
            HttpSession session,
            HttpServletRequest httpRequest
    ) {
        adminElectionService.delete(id, sessionCampusId(session), sessionDepartmentCode(session), httpRequest);
        return ResponseEntity.ok().build();
    }
}