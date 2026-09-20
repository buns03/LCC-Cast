package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.analytics.AnalyticsDTO.AdminDeptAnalyticsResponse;
import lccast.voting.system.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin-dept/analytics")
public class AdminDeptAnalyticsApiController {

    private final AnalyticsService analyticsService;

    public AdminDeptAnalyticsApiController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public AdminDeptAnalyticsResponse getMyDepartmentAnalytics(HttpSession session) {

        Object campusIdAttr = session.getAttribute("campusId");
        String programCourse = (String) session.getAttribute("programCourse");

        System.out.println("[ANALYTICS] sessionId=" + session.getId()
                + " campusIdAttr=" + campusIdAttr
                + " programCourse=" + programCourse);

        if (campusIdAttr == null || programCourse == null
                || "Supreme Student Council".equals(programCourse)) {
            System.out.println("[ANALYTICS] returning empty response early");
            return new AdminDeptAnalyticsResponse();
        }

        UUID campusId = campusIdAttr instanceof UUID
                ? (UUID) campusIdAttr
                : UUID.fromString(campusIdAttr.toString());

        return analyticsService.getAdminDeptAnalytics(campusId, programCourse);
    }
}