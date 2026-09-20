    package lccast.voting.system.dto.adminDept;

    import java.util.List;

    public class AdminDeptDashboardResponse {

        private final AdminDeptDashboardStatistics statistics;
        private final List<PositionVoteData> departmentVotes;

        public AdminDeptDashboardResponse(
                AdminDeptDashboardStatistics statistics,
                List<PositionVoteData> departmentVotes) {

            this.statistics = statistics;
            this.departmentVotes = departmentVotes;
        }

        public AdminDeptDashboardStatistics getStatistics() { return statistics; }
        public List<PositionVoteData> getDepartmentVotes() { return departmentVotes; }
    }