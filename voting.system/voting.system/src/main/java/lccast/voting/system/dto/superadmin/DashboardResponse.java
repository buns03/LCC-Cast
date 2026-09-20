package lccast.voting.system.dto.superadmin;

import java.util.List;

public class DashboardResponse {

    private DashboardStatistics statistics;

    private List<CampusVoteData> campusVotes;

    private List<ProgramVoteData> departmentVotes;

    private List<ProgramVoteData> sscVotes;

    public DashboardResponse(
            DashboardStatistics statistics,
            List<CampusVoteData> campusVotes,
            List<ProgramVoteData> departmentVotes,
            List<ProgramVoteData> sscVotes) {

        this.statistics = statistics;
        this.campusVotes = campusVotes;
        this.departmentVotes = departmentVotes;
        this.sscVotes = sscVotes;
    }

    public DashboardStatistics getStatistics() {
        return statistics;
    }

    public List<CampusVoteData> getCampusVotes() {
        return campusVotes;
    }

    public List<ProgramVoteData> getDepartmentVotes() {
        return departmentVotes;
    }

    public List<ProgramVoteData> getSscVotes() {
        return sscVotes;
    }
}