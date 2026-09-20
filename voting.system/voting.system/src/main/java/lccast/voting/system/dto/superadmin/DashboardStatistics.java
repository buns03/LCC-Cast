package lccast.voting.system.dto.superadmin;

public class DashboardStatistics {

    private long totalVoters;
    private long totalVoted;
    private long activeElections;
    private long totalCandidates;
    private long turnout;

    public DashboardStatistics() {
    }

    public DashboardStatistics(
            long totalVoters,
            long totalVoted,
            long activeElections,
            long totalCandidates) {

        this.totalVoters = totalVoters;
        this.totalVoted = totalVoted;
        this.activeElections = activeElections;
        this.totalCandidates = totalCandidates;

        this.turnout = totalVoters > 0
                ? Math.round(
                ((double) totalVoted / totalVoters) * 100
        )
                : 0;
    }

    public long getTotalVoters() {
        return totalVoters;
    }

    public long getTotalVoted() {
        return totalVoted;
    }

    public long getActiveElections() {
        return activeElections;
    }

    public long getTotalCandidates() {
        return totalCandidates;
    }

    public long getTurnout() {
        return turnout;
    }
}