package lccast.voting.system.service;

import lccast.voting.system.model.*;
import lccast.voting.system.repository.DepartmentRepository;
import lccast.voting.system.repository.ElectionDepartmentRepository;
import lccast.voting.system.repository.ElectionRepository;
import lccast.voting.system.util.DepartmentUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enforces: one ACTIVE election per school year for the SSC (per campus)
 * and for each department. Archived / deleted elections don't count.
 */
@Component
public class ElectionSchoolYearGuard {

    private static final UUID NO_ELECTION = new UUID(0L, 0L);

    private final ElectionRepository electionRepository;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final DepartmentRepository departmentRepository;

    public ElectionSchoolYearGuard(
            ElectionRepository electionRepository,
            ElectionDepartmentRepository electionDepartmentRepository,
            DepartmentRepository departmentRepository
    ) {
        this.electionRepository = electionRepository;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * @param excludeElectionId the election being edited or restored,
     *                          or null when creating a new one
     */


    public void assertAvailable(
            ElectionCategory category,
            UUID campusId,
            String schoolYear,
            List<UUID> departmentIds,
            UUID excludeElectionId
    ) {
        if (excludeElectionId != null
                && electionRepository.findById(excludeElectionId).map(Election::isDrawElection).orElse(false)) {
            return;
        }

        if (category == null || schoolYear == null) return;

        String sy = schoolYear.trim();
        UUID exclude = excludeElectionId != null ? excludeElectionId : NO_ELECTION;

        if (category == ElectionCategory.SSC) {

            boolean taken = electionRepository
                    .existsByCategoryAndCampusIdAndSchoolYearAndStatusAndIdNot(
                            ElectionCategory.SSC, campusId, sy, RecordStatus.ACTIVE, exclude
                    );

            if (taken) {
                throw new IllegalStateException(
                        "An active SSC election for school year " + sy
                                + " already exists for this campus. Only one election per school year is allowed"
                                + " — delete or archive the existing one first."
                );
            }
            return;
        }

        if (category == ElectionCategory.DEPARTMENT
                && departmentIds != null && !departmentIds.isEmpty()) {

            // Program codes (e.g. BSIT) of the departments being submitted
            Set<String> requestedCodes = departmentRepository.findAllById(departmentIds).stream()
                    .map(DepartmentUtils::extractProgramCode)
                    .filter(Objects::nonNull)
                    .map(code -> code.trim().toUpperCase())
                    .collect(Collectors.toSet());

            // Other ACTIVE department elections on this campus in the same school year
            List<Election> existing = electionRepository
                    .findByCategoryAndCampusIdAndSchoolYearAndStatusAndIdNot(
                            ElectionCategory.DEPARTMENT, campusId, sy, RecordStatus.ACTIVE, exclude
                    );

            for (Election other : existing) {
                for (ElectionDepartment link : electionDepartmentRepository.findByElectionId(other.getId())) {

                    String code = DepartmentUtils.extractProgramCode(link.getDepartment());

                    if (code != null && requestedCodes.contains(code.trim().toUpperCase())) {
                        throw new IllegalStateException(
                                "An active election for " + code.trim().toUpperCase()
                                        + " in school year " + sy + " already exists for this campus."
                                        + " Only one election per school year is allowed"
                                        + " — delete or archive the existing one first."
                        );
                    }
                }
            }
        }
    }
}