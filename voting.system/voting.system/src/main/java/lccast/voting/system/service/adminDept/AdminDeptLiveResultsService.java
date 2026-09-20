package lccast.voting.system.service.adminDept;

import lccast.voting.system.dto.EntityResultDTO;
import lccast.voting.system.model.Department;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.repository.DepartmentRepository;
import lccast.voting.system.service.LiveResultsService;
import lccast.voting.system.util.DepartmentUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AdminDeptLiveResultsService {

    private final LiveResultsService liveResultsService;
    private final DepartmentRepository departmentRepository;

    public AdminDeptLiveResultsService(
            LiveResultsService liveResultsService,
            DepartmentRepository departmentRepository
    ) {
        this.liveResultsService = liveResultsService;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<EntityResultDTO> getDepartmentLiveResultsForAdmin(UUID campusId, UUID departmentId) {

        if (campusId == null || departmentId == null) {
            return Collections.emptyList();
        }

        String scopedDeptId = departmentId.toString();
        String scopedCampusId = campusId.toString();

        return liveResultsService.getDepartmentLiveResults().stream()
                .filter(dto -> dto.getDepartmentIds() != null && dto.getDepartmentIds().contains(scopedDeptId))
                .filter(dto -> scopedCampusId.equals(dto.getCampusId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UUID resolveDepartmentId(UUID campusId, String adminDepartmentCode) {
        if (campusId == null || adminDepartmentCode == null) return null;

        return departmentRepository.findByCampusIdAndStatus(campusId, RecordStatus.ACTIVE).stream()
                .filter(d -> adminDepartmentCode.equalsIgnoreCase(DepartmentUtils.extractProgramCode(d)))
                .max(Comparator.comparing(Department::getCreatedAt))
                .map(Department::getId)
                .orElse(null);
    }
}