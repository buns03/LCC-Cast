package lccast.voting.system.dto;

public class SuperadminDashboardDTO {

    private long totalVoters;
    private long totalVoted;
    private double turnout;
    private long activeElections;
    private long totalCandidates;

    public SuperadminDashboardDTO() {
    }

    public long getTotalVoters() {
        return totalVoters;
    }

    public void setTotalVoters(long totalVoters) {
        this.totalVoters = totalVoters;
    }

    public long getTotalVoted() {
        return totalVoted;
    }

    public void setTotalVoted(long totalVoted) {
        this.totalVoted = totalVoted;
    }

    public double getTurnout() {
        return turnout;
    }

    public void setTurnout(double turnout) {
        this.turnout = turnout;
    }

    public long getActiveElections() {
        return activeElections;
    }

    public void setActiveElections(long activeElections) {
        this.activeElections = activeElections;
    }

    public long getTotalCandidates() {
        return totalCandidates;
    }

    public void setTotalCandidates(long totalCandidates) {
        this.totalCandidates = totalCandidates;
    }
}