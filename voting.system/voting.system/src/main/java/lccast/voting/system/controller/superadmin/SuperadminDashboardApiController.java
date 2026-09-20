package lccast.voting.system.controller.superadmin;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lccast.voting.system.dto.superadmin.DashboardResponse;
import lccast.voting.system.service.superadmin.DashboardService;

@RestController
@RequestMapping("/superadmin/api")
public class SuperadminDashboardApiController {

    private final DashboardService dashboardService;

    public SuperadminDashboardApiController(
            DashboardService dashboardService) {

        this.dashboardService =
                dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(defaultValue = "all")
            String campus) {

        return ResponseEntity.ok(
                dashboardService.getDashboard(campus)
        );
    }
}