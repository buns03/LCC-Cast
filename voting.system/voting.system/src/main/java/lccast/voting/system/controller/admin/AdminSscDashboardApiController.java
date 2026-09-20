package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.superadmin.DashboardResponse;
import lccast.voting.system.service.superadmin.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Dashboard API for admin-ssc.
 *
 * There is no campus parameter: the campus is always the one registered on
 * the session account, so the client cannot widen the scope.
 */
@RestController
@RequestMapping("/admin-ssc/api")
public class AdminSscDashboardApiController {

    private final DashboardService dashboardService;

    public AdminSscDashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(HttpSession session) {
        return ResponseEntity.ok(
                dashboardService.getDashboardForAdminSsc(sessionCampusId(session))
        );
    }

    private UUID sessionCampusId(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("campusId");

        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Session expired. Please log in again."
            );
        }

        if (value instanceof UUID uuid) {
            return uuid;
        }

        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session campusId.");
        }
    }
}