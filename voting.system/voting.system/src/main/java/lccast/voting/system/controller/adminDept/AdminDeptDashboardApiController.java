package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.service.superadmin.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import lccast.voting.system.dto.adminDept.AdminDeptDashboardResponse;
import lccast.voting.system.service.adminDept.AdminDeptDashboardService;

@RestController
@RequestMapping("/admin-dept")
public class AdminDeptDashboardApiController {

    private final DashboardService dashboardService;   // was AdminDeptDashboardService

    public AdminDeptDashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    public ResponseEntity<AdminDeptDashboardResponse> getDashboard(HttpSession session) {
        UUID campusId = (UUID) session.getAttribute("campusId");
        String program = (String) session.getAttribute("programCourse");
        return ResponseEntity.ok(dashboardService.getDashboardForAdminDept(campusId, program));
    }
}