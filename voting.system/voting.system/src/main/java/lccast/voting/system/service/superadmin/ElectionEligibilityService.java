package lccast.voting.system.service.superadmin;

import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ElectionEligibilityService {

    private final ElectionRepository electionRepository;
    private final VoterRepository voterRepository;
    private final ElectionDepartmentRepository electionDepartmentRepository;

    public ElectionEligibilityService(
            ElectionRepository electionRepository,
            VoterRepository voterRepository,
            ElectionDepartmentRepository electionDepartmentRepository
    ) {
        this.electionRepository = electionRepository;
        this.voterRepository = voterRepository;
        this.electionDepartmentRepository = electionDepartmentRepository;
    }

    public boolean isEligible(
            UUID electionId,
            UUID voterId,
            UUID departmentId
    ) {

        Election election = electionRepository
                .findByIdAndStatus(electionId, RecordStatus.ACTIVE)
                .orElseThrow(() ->
                        new IllegalArgumentException("Election not found.")
                );

        Voter voter = voterRepository
                .findByIdAndCampusIdAndStatus(
                        voterId,
                        election.getCampusId(),
                        RecordStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Voter is not eligible for this campus."
                        )
                );

        if (election.getCategory() == ElectionCategory.SSC) {
            return true;
        }

        if (election.getCategory() == ElectionCategory.DEPARTMENT) {

            if (departmentId == null) {
                return false;
            }

            return electionDepartmentRepository
                    .existsByElectionIdAndDepartmentId(
                            electionId,
                            departmentId
                    );
        }

        return false;
    }
}