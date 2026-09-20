package lccast.voting.system.service.student;

import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.superadmin.ElectionService;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class VoterDepartmentElectionService {

    private final DepartmentRepository departmentRepository;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final ElectionRepository electionRepository;
    private final ElectionService electionService;

    public VoterDepartmentElectionService(
            DepartmentRepository departmentRepository,
            ElectionDepartmentRepository electionDepartmentRepository,
            ElectionRepository electionRepository,
            ElectionService electionService
    ) {
        this.departmentRepository = departmentRepository;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.electionRepository = electionRepository;
        this.electionService = electionService;
    }

    public Department resolveVoterDepartment(Voter voter) {
        if (voter == null || voter.getProgramCourse() == null || voter.getCampusId() == null) {
            return null;
        }

        return departmentRepository
                .findByCampusIdAndStatus(voter.getCampusId(), RecordStatus.ACTIVE)
                .stream()
                .filter(d -> voter.getProgramCourse().equalsIgnoreCase(d.getName()))
                .findFirst()
                .orElse(null);
    }

    public List<Department> resolveVoterDepartments(Voter voter) {
        if (voter == null || voter.getProgramCourse() == null || voter.getCampusId() == null) {
            return List.of();
        }

        return departmentRepository
                .findByCampusIdAndStatus(voter.getCampusId(), RecordStatus.ACTIVE)
                .stream()
                .filter(d -> voter.getProgramCourse().equalsIgnoreCase(d.getName()))
                .toList();
    }

    public Optional<Election> resolveVisibleElection(Voter voter) {

        List<Department> departments = resolveVoterDepartments(voter);

        if (departments.isEmpty()) {
            return Optional.empty();
        }

        for (Department department : departments) {

            List<ElectionDepartment> links =
                    electionDepartmentRepository.findByDepartmentId(department.getId());

            for (ElectionDepartment link : links) {

                Election election = link.getElection();

                if (election.getStatus() != RecordStatus.ACTIVE) {
                    continue;
                }

                ElectionService.VoterElectionPhase phase =
                        electionService.calculateVoterPhase(election);

                if (phase == ElectionService.VoterElectionPhase.HIDDEN
                        || phase == ElectionService.VoterElectionPhase.CONCLUDED) {
                    continue;
                }

                return Optional.of(election);
            }
        }

        return Optional.empty();
    }

    public List<Department> resolveVoterDepartmentsForElection(Voter voter, UUID electionId) {
        List<Department> voterDepartments = resolveVoterDepartments(voter);

        if (voterDepartments.isEmpty()) {
            return List.of();
        }

        Set<UUID> linkedDepartmentIds = electionDepartmentRepository
                .findByElectionId(electionId)
                .stream()
                .map(link -> link.getDepartment().getId())
                .collect(java.util.stream.Collectors.toSet());

        return voterDepartments.stream()
                .filter(d -> linkedDepartmentIds.contains(d.getId()))
                .toList();
    }
}