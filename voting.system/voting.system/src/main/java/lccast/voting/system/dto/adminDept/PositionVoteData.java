package lccast.voting.system.dto.adminDept;

public class PositionVoteData {

    private final String position;
    private final long votes;

    public PositionVoteData(String position, long votes) {
        this.position = position;
        this.votes = votes;
    }

    public String getPosition() { return position; }
    public long getVotes() { return votes; }
}