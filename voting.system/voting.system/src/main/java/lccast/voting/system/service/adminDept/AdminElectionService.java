package lccast.voting.system.service.adminDept;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.dto.superadmin.ElectionResponse;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.DepartmentRepository;
import lccast.voting.system.repository.ElectionDepartmentRepository;
import lccast.voting.system.service.superadmin.ElectionService;
import lccast.voting.system.util.DepartmentUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AdminElectionService {

    private final ElectionService electionService;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final DepartmentRepository departmentRepository;

    public AdminElectionService(
            ElectionService electionService,
            ElectionDepartmentRepository electionDepartmentRepository,
            DepartmentRepository departmentRepository
    ) {
        this.electionService = electionService;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.departmentRepository = departmentRepository;
    }

    private void assertCampusMatches(UUID sessionCampusId, UUID requestCampusId) {
        if (sessionCampusId == null) {
            throw new IllegalStateException("No campus is registered to this account.");
        }
        if (!sessionCampusId.equals(requestCampusId)) {
            throw new IllegalArgumentException("You are not authorized to manage elections for that campus.");
        }
    }

    private void assertDepartmentsInScope(List<UUID> departmentIds, String adminDepartmentCode) {
        if (adminDepartmentCode == null || adminDepartmentCode.isBlank()) {
            throw new IllegalStateException("No department is registered to this account.");
        }

        if (departmentIds == null || departmentIds.isEmpty()) {
            throw new IllegalArgumentException("Department election requires at least one department.");
        }

        List<Department> departments = departmentRepository.findAllById(departmentIds);

        if (departments.size() != departmentIds.size()) {
            throw new IllegalArgumentException("One or more departments were not found.");
        }

        for (Department department : departments) {
            String code = DepartmentUtils.extractProgramCode(department);
            if (!adminDepartmentCode.equalsIgnoreCase(code)) {
                throw new IllegalArgumentException(
                        "You are not authorized to use department \"" + department.getTitle() + "\"."
                );
            }
        }
    }

    /** Elections visible to this admin: DEPARTMENT category, same campus, department in scope. */
    public List<ElectionResponse> getScopedElections(UUID sessionCampusId, String adminDepartmentCode) {
        return electionService.getByCampus(sessionCampusId).stream()
                .filter(e -> e.getCategory() == ElectionCategory.DEPARTMENT)
                .filter(e -> electionBelongsToDepartment(e.getId(), adminDepartmentCode))
                .map(electionService::toResponse)
                .toList();
    }

    private boolean electionBelongsToDepartment(UUID electionId, String adminDepartmentCode) {
        return electionDepartmentRepository.findByElectionId(electionId).stream()
                .map(ElectionDepartment::getDepartment)
                .map(DepartmentUtils::extractProgramCode)
                .anyMatch(code -> adminDepartmentCode.equalsIgnoreCase(code));
    }

    public ElectionResponse getScopedById(UUID id, UUID sessionCampusId, String adminDepartmentCode) {
        Election election = electionService.getById(id);

        assertCampusMatches(sessionCampusId, election.getCampusId());
        if (!electionBelongsToDepartment(id, adminDepartmentCode)) {
            throw new IllegalArgumentException("Election not found for this account.");
        }

        return electionService.toResponse(election);
    }

    public ElectionResponse create(
            String title,
            UUID campusId,
            String schoolYear,
            java.time.Instant startAt,
            java.time.Instant endAt,
            UUID createdBy,
            List<UUID> departmentIds,
            UUID sessionCampusId,
            String adminDepartmentCode,
            HttpServletRequest request
    ) {
        assertCampusMatches(sessionCampusId, campusId);
        assertDepartmentsInScope(departmentIds, adminDepartmentCode);

        Election saved = electionService.create(
                title, ElectionCategory.DEPARTMENT, campusId, schoolYear,
                startAt, endAt, createdBy, List.of(), departmentIds, request
        );

        return electionService.toResponse(saved);
    }

    public ElectionResponse update(
            UUID id,
            String title,
            UUID campusId,
            String schoolYear,
            java.time.Instant startAt,
            java.time.Instant endAt,
            List<UUID> departmentIds,
            UUID sessionCampusId,
            String adminDepartmentCode,
            HttpServletRequest request
    ) {
        // Reject if the election isn't in this admin's scope BEFORE mutating it.
        getScopedById(id, sessionCampusId, adminDepartmentCode);

        assertCampusMatches(sessionCampusId, campusId);
        assertDepartmentsInScope(departmentIds, adminDepartmentCode);
        if (!electionService.getById(id).isDrawElection()) {
            assertDepartmentsInScope(departmentIds, adminDepartmentCode);
        }
        Election saved = electionService.update(
                id, title, ElectionCategory.DEPARTMENT, campusId, schoolYear,
                startAt, endAt, List.of(), departmentIds, request
        );

        return electionService.toResponse(saved);
    }

    public void archive(UUID id, UUID sessionCampusId, String adminDepartmentCode, HttpServletRequest request) {
        getScopedById(id, sessionCampusId, adminDepartmentCode);
        electionService.archive(id, request);
    }

    public void delete(UUID id, UUID sessionCampusId, String adminDepartmentCode, HttpServletRequest request) {
        getScopedById(id, sessionCampusId, adminDepartmentCode);
        electionService.delete(id, request);
    }

    public ElectionResponse scheduleDraw(UUID id, UUID sessionCampusId, String adminDepartmentCode,
                                         java.time.Instant startAt, java.time.Instant endAt,
                                         HttpServletRequest request) {
        getScopedById(id, sessionCampusId, adminDepartmentCode);
        return electionService.toResponse(electionService.scheduleDrawElection(id, startAt, endAt, request));
    }
}