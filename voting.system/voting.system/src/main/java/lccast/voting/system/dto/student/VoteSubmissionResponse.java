package lccast.voting.system.dto.student;

import java.time.Instant;
import java.util.UUID;

public class VoteSubmissionResponse {

    private UUID ballotId;
    private Instant votedAt;
    private String message;

    public UUID getBallotId() { return ballotId; }
    public void setBallotId(UUID ballotId) { this.ballotId = ballotId; }

    public Instant getVotedAt() { return votedAt; }
    public void setVotedAt(Instant votedAt) { this.votedAt = votedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}