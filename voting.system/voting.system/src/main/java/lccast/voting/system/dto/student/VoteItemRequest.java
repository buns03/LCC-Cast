package lccast.voting.system.dto.student;

import java.util.UUID;

public class VoteItemRequest {

    private String position;
    private UUID candidateId;
    private boolean skipped;

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }

    public boolean isSkipped() { return skipped; }
    public void setSkipped(boolean skipped) { this.skipped = skipped; }
}