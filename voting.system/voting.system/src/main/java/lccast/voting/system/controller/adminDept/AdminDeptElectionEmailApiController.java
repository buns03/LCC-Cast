package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.ElectionEmailCampaign;
import lccast.voting.system.service.adminDept.AdminElectionService;
import lccast.voting.system.service.superadmin.ElectionEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-dept/api/elections/{electionId}/email")
public class AdminDeptElectionEmailApiController {

    private final ElectionEmailService electionEmailService;
    private final AdminElectionService adminElectionService;

    public AdminDeptElectionEmailApiController(
            ElectionEmailService electionEmailService,
            AdminElectionService adminElectionService
    ) {
        this.electionEmailService = electionEmailService;
        this.adminElectionService = adminElectionService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException ex
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(
            IllegalStateException ex
    ) {
        return ResponseEntity.status(409)
                .body(Map.of("message", ex.getMessage()));
    }

    private UUID sessionCampusId(HttpSession session) {
        Object raw = session.getAttribute("campusId");

        if (raw == null) {
            return null;
        }

        return UUID.fromString(raw.toString());
    }

    private String sessionDepartmentCode(HttpSession session) {
        Object raw = session.getAttribute("programCourse");

        if (raw == null) {
            return null;
        }

        return raw.toString().trim().toUpperCase();
    }

    private UUID sessionUserId(HttpSession session) {
        Object raw = session.getAttribute("userId");

        if (raw == null) {
            return null;
        }

        return UUID.fromString(raw.toString());
    }

    private boolean authorized(
            UUID electionId,
            HttpSession session
    ) {
        UUID campusId = sessionCampusId(session);
        String departmentCode = sessionDepartmentCode(session);

        if (campusId == null || departmentCode == null) {
            return false;
        }

        // Also verifies that this election belongs to
        // the logged-in admin's campus and department.
        adminElectionService.getScopedById(
                electionId,
                campusId,
                departmentCode
        );

        return true;
    }

    /*
     * =========================================================
     * EMAIL STATUS
     * =========================================================
     */

    @GetMapping("/status")
    public ResponseEntity<?> getEmailStatus(
            @PathVariable UUID electionId,
            HttpSession session
    ) {

        if (!authorized(electionId, session)) {
            return ResponseEntity.status(401).build();
        }

        long eligibleVoterCount =
                electionEmailService.getEligibleVoterCount(electionId);

        var scheduled =
                electionEmailService.getScheduledStatus(electionId);

        Map<String, Object> response = new java.util.HashMap<>();

        response.put("eligibleVoterCount", eligibleVoterCount);
        response.put("scheduled", scheduled.isPresent());
        response.put(
                "scheduledAt",
                scheduled.map(ElectionEmailCampaign::getScheduledAt)
                        .orElse(null)
        );

        return ResponseEntity.ok(response);
        
    }

    /*
     * =========================================================
     * SEND NOW
     * =========================================================
     */

    @PostMapping("/send")
    public ResponseEntity<?> sendNow(
            @PathVariable UUID electionId,
            HttpSession session,
            HttpServletRequest request
    ) {

        UUID adminId = sessionUserId(session);

        if (adminId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!authorized(electionId, session)) {
            return ResponseEntity.status(401).build();
        }

        ElectionEmailCampaign campaign =
                electionEmailService.sendNow(
                        electionId,
                        adminId,
                        request
                );

        return ResponseEntity.ok(
                Map.of(
                        "message", "Election email sent successfully.",
                        "campaignId", campaign.getId(),
                        "status", campaign.getStatus()
                )
        );
    }

    /*
     * =========================================================
     * SCHEDULE
     * =========================================================
     */

    @PostMapping("/schedule")
    public ResponseEntity<?> schedule(
            @PathVariable UUID electionId,
            @RequestBody ScheduleEmailRequest body,
            HttpSession session,
            HttpServletRequest request
    ) {

        UUID adminId = sessionUserId(session);

        if (adminId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!authorized(electionId, session)) {
            return ResponseEntity.status(401).build();
        }

        ElectionEmailCampaign campaign =
                electionEmailService.schedule(
                        electionId,
                        body.scheduledAt(),
                        adminId,
                        request
                );

        return ResponseEntity.ok(
                Map.of(
                        "message", "Election email scheduled successfully.",
                        "campaignId", campaign.getId(),
                        "status", campaign.getStatus(),
                        "scheduledAt", campaign.getScheduledAt()
                )
        );
    }

    /*
     * =========================================================
     * CANCEL SCHEDULE
     * =========================================================
     */

    @DeleteMapping("/schedule")
    public ResponseEntity<?> cancelSchedule(
            @PathVariable UUID electionId,
            HttpSession session,
            HttpServletRequest request
    ) {

        if (!authorized(electionId, session)) {
            return ResponseEntity.status(401).build();
        }

        electionEmailService.cancelScheduled(
                electionId,
                request
        );

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Scheduled election email cancelled."
                )
        );
    }

    /*
     * =========================================================
     * REQUEST BODY
     * =========================================================
     */

    public record ScheduleEmailRequest(
            Instant scheduledAt
    ) {
    }
}