package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.EntityResultDTO;
import lccast.voting.system.service.LiveResultsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-ssc/live-results")
public class AdminSscLiveResultsApiController {

    private final LiveResultsService liveResultsService;

    public AdminSscLiveResultsApiController(LiveResultsService liveResultsService) {
        this.liveResultsService = liveResultsService;
    }

    private UUID requireCampusId(HttpSession session) {
        Object campusId = session.getAttribute("campusId");
        if (campusId == null) throw new RuntimeException("No campus is associated with this account.");
        return campusId instanceof UUID ? (UUID) campusId : UUID.fromString(campusId.toString());
    }

    @GetMapping("/data")
    public Map<String, List<EntityResultDTO>> getLiveResultsData(HttpSession session) {
        return Map.of("ssc", liveResultsService.getSscLiveResultsForCampus(requireCampusId(session)));
    }
}