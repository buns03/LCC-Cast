package lccast.voting.system.service.superadmin;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lccast.voting.system.dto.adminDept.AdminDeptDashboardResponse;
import lccast.voting.system.dto.adminDept.AdminDeptDashboardStatistics;
import lccast.voting.system.dto.adminDept.PositionVoteData;
import lccast.voting.system.dto.superadmin.CampusVoteData;
import lccast.voting.system.dto.superadmin.DashboardResponse;
import lccast.voting.system.dto.superadmin.DashboardStatistics;
import lccast.voting.system.dto.superadmin.ProgramVoteData;
import lccast.voting.system.repository.superadmin.DashboardRepository;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<String> POSITION_ORDER = List.of(
            "President", "Vice President", "Secretary", "Treasurer",
            "Auditor", "PRO Internal", "PRO External");

    private final DashboardRepository repo;

    public DashboardService(DashboardRepository repo) {
        this.repo = repo;
    }

    // ---------- superadmin: campus name or "all" ----------
    public DashboardResponse getDashboard(String campus) {
        UUID campusId = resolveCampusId(campus);
        return build(campusId, null, true);
    }

    // ---------- admin-ssc: campus only ----------
    public DashboardResponse getDashboardForAdminSsc(UUID campusId) {
        if (campusId == null) {
            throw new IllegalStateException("Missing campusId in session — admin-ssc not scoped.");
        }
        return build(campusId, null, false);
    }

    // ---------- admin-dept: campus + program ----------
    public AdminDeptDashboardResponse getDashboardForAdminDept(UUID campusId, String program) {
        if (campusId == null || program == null || program.isBlank()) {
            throw new IllegalStateException(
                    "Missing campusId/programCourse in session — admin-dept not scoped.");
        }
        String code = program.trim();

        AdminDeptDashboardStatistics statistics = new AdminDeptDashboardStatistics(
                repo.countVoters(campusId, code),
                repo.countVoted(campusId, code),
                repo.countActiveElections(campusId, code),
                repo.countCandidates(campusId, code));

        List<PositionVoteData> positions = repo.votesByPosition(campusId, code).stream()
                .map(r -> new PositionVoteData((String) r[0], count(r[1])))
                .sorted(Comparator.comparingInt(p -> positionRank(p.getPosition())))
                .toList();

        return new AdminDeptDashboardResponse(statistics, positions);
    }

    // ---------- shared builder ----------
    private DashboardResponse build(UUID campusId, String program, boolean fullGraphs) {
        long totalVoters = repo.countVoters(campusId, program);
        long totalVoted = repo.countVoted(campusId, program);

        DashboardStatistics statistics = new DashboardStatistics(
                totalVoters,
                totalVoted,
                repo.countActiveElections(campusId, program),
                repo.countCandidates(campusId, program));

        List<ProgramVoteData> sscVotes = List.of(
                new ProgramVoteData("Voted", totalVoted),
                new ProgramVoteData("Not Voted", Math.max(0, totalVoters - totalVoted)));

        if (!fullGraphs) {
            return new DashboardResponse(statistics, List.of(), List.of(), sscVotes);
        }

        List<CampusVoteData> campusVotes = repo.votedByCampus().stream()
                .map(r -> new CampusVoteData((String) r[0], count(r[1])))
                .toList();

        List<ProgramVoteData> departmentVotes = repo.votedByProgram(campusId).stream()
                .map(r -> new ProgramVoteData((String) r[0], count(r[1])))
                .toList();

        return new DashboardResponse(statistics, campusVotes, departmentVotes, sscVotes);
    }

    private UUID resolveCampusId(String campus) {
        if (campus == null || campus.isBlank() || campus.equalsIgnoreCase("all")) {
            return null;
        }
        return repo.findCampusIdByName(campus.trim()).orElse(null);
    }

    private static int positionRank(String position) {
        int i = POSITION_ORDER.indexOf(position);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static long count(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}