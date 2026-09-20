package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.EntityResultDTO;
import lccast.voting.system.service.adminDept.AdminDeptLiveResultsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-dept/live-results")
public class AdminDeptLiveResultsApiController {

    private final AdminDeptLiveResultsService adminDeptLiveResultsService;

    public AdminDeptLiveResultsApiController(AdminDeptLiveResultsService adminDeptLiveResultsService) {
        this.adminDeptLiveResultsService = adminDeptLiveResultsService;
    }

    @GetMapping("/data")
    public Map<String, List<EntityResultDTO>> getLiveResultsData(HttpSession session) {
        UUID campusId = (UUID) session.getAttribute("campusId");
        String programCourse = (String) session.getAttribute("programCourse");

        System.out.println("[LIVE-RESULTS API] campusId=" + campusId + " programCourse=" + programCourse);

        UUID departmentId = adminDeptLiveResultsService.resolveDepartmentId(campusId, programCourse);
        System.out.println("[LIVE-RESULTS API] resolved departmentId=" + departmentId);

        return Map.of("departments",
                adminDeptLiveResultsService.getDepartmentLiveResultsForAdmin(campusId, departmentId));
    }
}