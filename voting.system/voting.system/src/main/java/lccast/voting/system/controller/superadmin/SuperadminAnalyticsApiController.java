package lccast.voting.system.controller.superadmin;

import lccast.voting.system.dto.analytics.AnalyticsDTO.FullAnalytics;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.ElectionDepartmentRepository;
import lccast.voting.system.repository.ElectionRepository;
import lccast.voting.system.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/superadmin/analytics")
public class SuperadminAnalyticsApiController {

    private final AnalyticsService analyticsService;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final ElectionRepository electionRepository;

    public SuperadminAnalyticsApiController(
            AnalyticsService analyticsService,
            ElectionDepartmentRepository electionDepartmentRepository,
            ElectionRepository electionRepository) {
        this.analyticsService = analyticsService;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.electionRepository = electionRepository;
    }

    @GetMapping
    public FullAnalytics getFullAnalytics() {
        return analyticsService.getFullAnalytics();
    }

    @GetMapping("/departments")
    public List<Map<String, String>> getElectionDepartments() {
        List<Election> elections = electionRepository.findByCategoryAndStatus(
                ElectionCategory.DEPARTMENT, RecordStatus.ACTIVE);

        return elections.stream()
                .map(e -> {
                    List<Department> depts = e.getElectionDepartments().stream()
                            .map(ElectionDepartment::getDepartment)
                            .collect(Collectors.toList());
                    String votingType = depts.isEmpty() || depts.get(0).getVotingType() == null
                            ? "" : depts.get(0).getVotingType().name();
                    return Map.of(
                            "id", e.getId().toString(),
                            "name", e.getTitle(),
                            "title", e.getTitle(),
                            "votingType", votingType
                    );
                })
                .collect(Collectors.toList());
    }
}