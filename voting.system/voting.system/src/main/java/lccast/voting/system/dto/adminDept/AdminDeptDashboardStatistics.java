package lccast.voting.system.dto.adminDept;

public class AdminDeptDashboardStatistics {

    private final long totalVoters;
    private final long totalVoted;
    private final long activeElection;
    private final long totalCandidates;

    public AdminDeptDashboardStatistics(
            long totalVoters,
            long totalVoted,
            long activeElection,
            long totalCandidates) {

        this.totalVoters = totalVoters;
        this.totalVoted = totalVoted;
        this.activeElection = activeElection;
        this.totalCandidates = totalCandidates;
    }

    public long getTotalVoters() { return totalVoters; }
    public long getTotalVoted() { return totalVoted; }
    public long getActiveElection() { return activeElection; }
    public long getTotalCandidates() { return totalCandidates; }
}