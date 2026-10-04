package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.analytics.AnalyticsDTO.CampusAnalytics;
import lccast.voting.system.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin-ssc/api/analytics")
public class AdminSscAnalyticsApiController {

    private final AnalyticsService analyticsService;

    public AdminSscAnalyticsApiController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    private UUID requireCampusId(HttpSession session) {
        Object campusId = session.getAttribute("campusId");
        if (campusId == null) throw new RuntimeException("No campus is associated with this account.");
        return campusId instanceof UUID ? (UUID) campusId : UUID.fromString(campusId.toString());
    }

    @GetMapping
    public CampusAnalytics getAnalytics(HttpSession session) {
        return analyticsService.getSscAnalyticsForCampus(requireCampusId(session));
    }

    @GetMapping("/history")
    public List<CampusAnalytics> getHistory(HttpSession session) {
        return analyticsService.getSscAnalyticsHistoryForCampus(requireCampusId(session));
    }
}