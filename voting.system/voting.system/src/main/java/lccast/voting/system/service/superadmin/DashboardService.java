package lccast.voting.system.service.superadmin;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lccast.voting.system.dto.superadmin.CampusVoteData;
import lccast.voting.system.dto.superadmin.DashboardResponse;
import lccast.voting.system.dto.superadmin.DashboardStatistics;
import lccast.voting.system.dto.superadmin.ProgramVoteData;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.VotingStatus;
import lccast.voting.system.repository.superadmin.DashboardRepository;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardService(
            DashboardRepository dashboardRepository) {

        this.dashboardRepository =
                dashboardRepository;
    }


    // =====================================================
    // GET DASHBOARD
    // =====================================================

    public DashboardResponse getDashboard(String campus) {

        UUID campusId =
                resolveCampusId(campus);

        long totalVoters;

        long totalVoted;

        long activeElection;

        long totalCandidates;

        List<ProgramVoteData> departmentVotes;


        // =====================================================
        // ALL CAMPUSES
        // =====================================================

        if (campusId == null) {

            totalVoters =
                    dashboardRepository.countAllActiveVoters(
                            RecordStatus.ACTIVE
                    );

            totalVoted =
                    dashboardRepository.countVoted(
                            RecordStatus.ACTIVE,
                            VotingStatus.VOTED
                    );

            activeElection =
                    dashboardRepository.countActiveElections(
                            RecordStatus.ACTIVE
                    );

            totalCandidates =
                    dashboardRepository.countCandidates();

            departmentVotes =
                    dashboardRepository.getDepartmentVotes();
        }


        // =====================================================
        // SINGLE CAMPUS
        // =====================================================

        else {

            totalVoters =
                    dashboardRepository.countActiveVotersByCampus(
                            RecordStatus.ACTIVE,
                            campusId
                    );

            totalVoted =
                    dashboardRepository.countVotedByCampus(
                            RecordStatus.ACTIVE,
                            VotingStatus.VOTED,
                            campusId
                    );

            activeElection =
                    dashboardRepository.countActiveElectionsByCampus(
                            RecordStatus.ACTIVE,
                            campusId
                    );

            totalCandidates =
                    dashboardRepository.countCandidatesByCampus(
                            campusId
                    );

            departmentVotes =
                    dashboardRepository.getDepartmentVotesByCampus(
                            campusId
                    );
        }


        // =====================================================
        // STATISTICS
        // =====================================================

        DashboardStatistics statistics =
                new DashboardStatistics(
                        totalVoters,
                        totalVoted,
                        activeElection,
                        totalCandidates
                );


        // =====================================================
        // CAMPUS GRAPH
        // =====================================================

        List<CampusVoteData> campusVotes =
                dashboardRepository.getVotesByCampus(
                        RecordStatus.ACTIVE,
                        VotingStatus.VOTED
                );


        // =====================================================
        // SSC GRAPH
        // =====================================================

        List<ProgramVoteData> sscVotes =
                List.of(

                        new ProgramVoteData(
                                "Voted",
                                totalVoted
                        ),

                        new ProgramVoteData(
                                "Not Voted",
                                Math.max(
                                        0,
                                        totalVoters - totalVoted
                                )
                        )
                );


        // =====================================================
        // RESPONSE
        // =====================================================

        return new DashboardResponse(
                statistics,
                campusVotes,
                departmentVotes,
                sscVotes
        );
    }


    // =====================================================
    // CAMPUS NAME → CAMPUS ID
    // =====================================================

    private UUID resolveCampusId(String campus) {

        if (campus == null ||
                campus.trim().isEmpty() ||
                campus.equalsIgnoreCase("all")) {

            return null;
        }

        return dashboardRepository
                .findCampusByNameIgnoreCase(campus.trim())
                .map(c -> c.getId())
                .orElse(null);
    }

    public DashboardResponse getDashboardForAdminSsc(UUID campusId) {

        long totalVoters =
                dashboardRepository.countActiveVotersByCampus(
                        RecordStatus.ACTIVE,
                        campusId
                );

        long totalVoted =
                dashboardRepository.countVotedByCampus(
                        RecordStatus.ACTIVE,
                        VotingStatus.VOTED,
                        campusId
                );

        long activeElection =
                dashboardRepository.countActiveElectionsByCampus(
                        RecordStatus.ACTIVE,
                        campusId
                );

        long totalCandidates =
                dashboardRepository.countCandidatesByCampus(
                        campusId
                );

        DashboardStatistics statistics =
                new DashboardStatistics(
                        totalVoters,
                        totalVoted,
                        activeElection,
                        totalCandidates
                );

        List<ProgramVoteData> sscVotes =
                List.of(
                        new ProgramVoteData(
                                "Voted",
                                totalVoted
                        ),
                        new ProgramVoteData(
                                "Not Voted",
                                Math.max(
                                        0,
                                        totalVoters - totalVoted
                                )
                        )
                );

        return new DashboardResponse(
                statistics,
                List.of(),   // campusVotes — not used by admin-ssc
                List.of(),   // departmentVotes — not used by admin-ssc
                sscVotes
        );
    }
}