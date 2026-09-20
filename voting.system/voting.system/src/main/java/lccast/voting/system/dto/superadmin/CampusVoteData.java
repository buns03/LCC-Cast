package lccast.voting.system.dto.superadmin;

public class CampusVoteData {

    private String campus;
    private long votes;

    public CampusVoteData() {
    }

    public CampusVoteData(String campus, long votes) {
        this.campus = campus;
        this.votes = votes;
    }

    public String getCampus() {
        return campus;
    }

    public long getVotes() {
        return votes;
    }
}