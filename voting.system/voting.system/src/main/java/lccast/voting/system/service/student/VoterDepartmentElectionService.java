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

//    public Optional<Election> resolveVisibleElection(Voter voter) {
//
//        List<Department> departments = resolveVoterDepartments(voter);
//
//        if (departments.isEmpty()) {
//            return Optional.empty();
//        }
//
//        for (Department department : departments) {
//
//            List<ElectionDepartment> links =
//                    electionDepartmentRepository.findByDepartmentId(department.getId());
//
//            for (ElectionDepartment link : links) {
//
//                Election election = link.getElection();
//
//                if (election.getStatus() != RecordStatus.ACTIVE) {
//                    continue;
//                }
//
//                ElectionService.VoterElectionPhase phase =
//                        electionService.calculateVoterPhase(election);
//
//                if (phase == ElectionService.VoterElectionPhase.HIDDEN
//                        || phase == ElectionService.VoterElectionPhase.CONCLUDED) {
//                    continue;
//                }
//
//                return Optional.of(election);
//            }
//        }
//
//        return Optional.empty();
//    }

    public Optional<Election> resolveVisibleElection(Voter voter) {
        return pickBest(voter, false);
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

    /**
     * Like resolveVisibleElection, but for STATUS DISPLAY (voter lists, exports),
     * not voting eligibility. A concluded election can still have a recorded vote
     * worth showing, so only HIDDEN (not yet open) is excluded here.
     */
    public Optional<Election> resolveElectionForStatus(Voter voter) {
        return pickBest(voter, true);
    }

    private Optional<Election> pickBest(Voter voter, boolean includeConcluded) {
        Election best = null;
        int bestRank = Integer.MAX_VALUE;

        for (Department department : resolveVoterDepartments(voter)) {
            for (ElectionDepartment link : electionDepartmentRepository.findByDepartmentId(department.getId())) {
                Election election = link.getElection();
                if (election.getStatus() != RecordStatus.ACTIVE) continue;

                int rank = switch (electionService.calculateVoterPhase(election)) {
                    case ONGOING -> 0;
                    case UPCOMING -> 1;
                    case CONCLUDED -> includeConcluded ? 2 : -1;
                    case HIDDEN -> -1;
                };
                if (rank < 0) continue;

                if (best == null || rank < bestRank
                        || (rank == bestRank && election.getEndAt().isAfter(best.getEndAt()))) {
                    best = election;
                    bestRank = rank;
                }
            }
        }
        return Optional.ofNullable(best);
    }
}