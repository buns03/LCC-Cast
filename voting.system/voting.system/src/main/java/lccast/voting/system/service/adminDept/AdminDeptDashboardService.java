package lccast.voting.system.service.adminDept;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lccast.voting.system.dto.adminDept.AdminDeptDashboardResponse;
import lccast.voting.system.dto.adminDept.AdminDeptDashboardStatistics;
import lccast.voting.system.dto.adminDept.PositionVoteData;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.VotingStatus;
import lccast.voting.system.repository.adminDept.AdminDeptDashboardRepository;

@Service
public class AdminDeptDashboardService {

    private final AdminDeptDashboardRepository adminDeptDashboardRepository;

    public AdminDeptDashboardService(
            AdminDeptDashboardRepository adminDeptDashboardRepository) {

        this.adminDeptDashboardRepository = adminDeptDashboardRepository;
    }

    public AdminDeptDashboardResponse getDashboard(UUID campusId, String departmentCode) {

        if (campusId == null || departmentCode == null || departmentCode.isBlank()) {
            throw new IllegalStateException(
                    "Missing campusId/programCourse in session — admin-dept user not properly scoped."
            );
        }

        long totalVoters = adminDeptDashboardRepository.countActiveVotersByCampusAndProgram(
                RecordStatus.ACTIVE, campusId, departmentCode);

        long totalVoted = adminDeptDashboardRepository.countVotedByCampusAndProgram(
                RecordStatus.ACTIVE, VotingStatus.VOTED, campusId, departmentCode);

        long activeElection = adminDeptDashboardRepository.countActiveElectionsByDepartmentCode(
                RecordStatus.ACTIVE, campusId, departmentCode);

        long totalCandidates = adminDeptDashboardRepository.countCandidatesByDepartmentCode(
                campusId, departmentCode);

        List<PositionVoteData> departmentVotes =
                adminDeptDashboardRepository.getVotesByPositionForDepartmentCode(
                        campusId, departmentCode);

        AdminDeptDashboardStatistics statistics = new AdminDeptDashboardStatistics(
                totalVoters, totalVoted, activeElection, totalCandidates);

        return new AdminDeptDashboardResponse(statistics, departmentVotes);
    }
}