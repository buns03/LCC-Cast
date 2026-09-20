package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.superadmin.ScheduleEmailRequest;
import lccast.voting.system.model.ElectionEmailCampaign;
import lccast.voting.system.service.admin.AdminSscElectionService;
import lccast.voting.system.service.superadmin.ElectionEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-ssc/api/elections/{electionId}/email")
public class AdminSscElectionEmailApiController {

    private final ElectionEmailService electionEmailService;
    private final AdminSscElectionService adminSscElectionService;

    public AdminSscElectionEmailApiController(
            ElectionEmailService electionEmailService,
            AdminSscElectionService adminSscElectionService
    ) {
        this.electionEmailService = electionEmailService;
        this.adminSscElectionService = adminSscElectionService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
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

    // Confirms the election belongs to this admin's campus before any email action touches it.
    private void verifyOwnership(UUID electionId, HttpSession session) {
        adminSscElectionService.getById(electionId, requireCampusId(session));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable UUID electionId, HttpSession session) {
        verifyOwnership(electionId, session);

        Map<String, Object> body = new HashMap<>();
        body.put("eligibleVoterCount", electionEmailService.getEligibleVoterCount(electionId));

        electionEmailService.getScheduledStatus(electionId).ifPresentOrElse(
                campaign -> {
                    body.put("scheduled", true);
                    body.put("scheduledAt", campaign.getScheduledAt());
                },
                () -> body.put("scheduled", false)
        );

        return ResponseEntity.ok(body);
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendNow(
            @PathVariable UUID electionId, HttpSession session, HttpServletRequest request) {

        verifyOwnership(electionId, session);

        UUID adminId = requireUserId(session);
        ElectionEmailCampaign campaign = electionEmailService.sendNow(electionId, adminId, request);
        return ResponseEntity.ok(Map.of("status", campaign.getStatus(), "sentAt", campaign.getSentAt()));
    }

    @PostMapping("/schedule")
    public ResponseEntity<?> schedule(
            @PathVariable UUID electionId,
            @RequestBody ScheduleEmailRequest body,
            HttpSession session,
            HttpServletRequest request) {

        verifyOwnership(electionId, session);

        UUID adminId = requireUserId(session);
        ElectionEmailCampaign campaign = electionEmailService.schedule(
                electionId, body.getScheduledAt(), adminId, request);

        return ResponseEntity.ok(Map.of(
                "status", campaign.getStatus(),
                "scheduledAt", campaign.getScheduledAt()
        ));
    }

    @DeleteMapping("/schedule")
    public ResponseEntity<Void> cancelSchedule(
            @PathVariable UUID electionId, HttpSession session, HttpServletRequest request) {

        verifyOwnership(electionId, session);

        electionEmailService.cancelScheduled(electionId, request);
        return ResponseEntity.ok().build();
    }
}