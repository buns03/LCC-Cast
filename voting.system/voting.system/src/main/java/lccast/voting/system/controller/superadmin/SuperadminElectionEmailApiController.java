package lccast.voting.system.controller.superadmin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.superadmin.ScheduleEmailRequest;
import lccast.voting.system.model.ElectionEmailCampaign;
import lccast.voting.system.service.superadmin.ElectionEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/superadmin/api/elections/{electionId}/email")
public class SuperadminElectionEmailApiController {

    private final ElectionEmailService electionEmailService;

    public SuperadminElectionEmailApiController(ElectionEmailService electionEmailService) {
        this.electionEmailService = electionEmailService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
    }

    private UUID resolveUserId(HttpSession session) {
        Object userIdAttribute = session.getAttribute("userId");
        if (userIdAttribute == null) return null;
        return userIdAttribute instanceof UUID
                ? (UUID) userIdAttribute
                : UUID.fromString(userIdAttribute.toString());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable UUID electionId) {
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

        UUID adminId = resolveUserId(session);
        if (adminId == null) return ResponseEntity.status(401).build();

        ElectionEmailCampaign campaign = electionEmailService.sendNow(electionId, adminId, request);
        return ResponseEntity.ok(Map.of("status", campaign.getStatus(), "sentAt", campaign.getSentAt()));
    }

    @PostMapping("/schedule")
    public ResponseEntity<?> schedule(
            @PathVariable UUID electionId,
            @RequestBody ScheduleEmailRequest body,
            HttpSession session,
            HttpServletRequest request) {

        UUID adminId = resolveUserId(session);
        if (adminId == null) return ResponseEntity.status(401).build();

        ElectionEmailCampaign campaign = electionEmailService.schedule(
                electionId, body.getScheduledAt(), adminId, request);

        return ResponseEntity.ok(Map.of(
                "status", campaign.getStatus(),
                "scheduledAt", campaign.getScheduledAt()
        ));
    }

    @DeleteMapping("/schedule")
    public ResponseEntity<Void> cancelSchedule(
            @PathVariable UUID electionId, HttpServletRequest request) {

        electionEmailService.cancelScheduled(electionId, request);
        return ResponseEntity.ok().build();
    }
}