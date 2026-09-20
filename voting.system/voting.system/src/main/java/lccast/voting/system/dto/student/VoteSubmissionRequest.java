package lccast.voting.system.dto.student;

import java.util.List;
import java.util.UUID;

public class VoteSubmissionRequest {

    private UUID electionId;
    private List<VoteItemRequest> votes;

    public UUID getElectionId() { return electionId; }
    public void setElectionId(UUID electionId) { this.electionId = electionId; }

    public List<VoteItemRequest> getVotes() { return votes; }
    public void setVotes(List<VoteItemRequest> votes) { this.votes = votes; }
}