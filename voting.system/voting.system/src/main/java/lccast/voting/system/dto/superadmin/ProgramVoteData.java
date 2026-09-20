package lccast.voting.system.dto.superadmin;

public class ProgramVoteData {

    private String program;
    private long votes;

    public ProgramVoteData() {
    }

    public ProgramVoteData(String program, long votes) {
        this.program = program;
        this.votes = votes;
    }

    public String getProgram() {
        return program;
    }

    public long getVotes() {
        return votes;
    }
}