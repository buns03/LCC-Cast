package lccast.voting.system.controller.superadmin;

import lccast.voting.system.dto.EntityResultDTO;
import lccast.voting.system.service.LiveResultsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/superadmin/live-results")
public class SuperadminLiveResultsApiController {

    private final LiveResultsService liveResultsService;

    public SuperadminLiveResultsApiController(LiveResultsService liveResultsService) {
        this.liveResultsService = liveResultsService;
    }

    @GetMapping("/data")
    public Map<String, List<EntityResultDTO>> getLiveResultsData() {
        return Map.of(
                "ssc", liveResultsService.getSscLiveResults(),
                "departments", liveResultsService.getDepartmentLiveResults()
        );
    }
}