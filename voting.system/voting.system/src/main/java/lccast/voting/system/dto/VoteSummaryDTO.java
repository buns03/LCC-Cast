package lccast.voting.system.dto;

import java.time.Instant;
import java.util.List;

public class VoteSummaryDTO {

    private String id;              // ballot id as string
    private String type;            // "ssc" or "department"
    private String votingType;      // "REPRESENTATIVE" / "PARTYLIST" / null for SSC
    private String electionTitle;
    private String electionLabel;
    private String campus;
    private String department;      // department name, null for SSC
    private Instant votedAt;
    private List<CandidateVoteDTO> candidates;

    public VoteSummaryDTO(String id, String type, String votingType, String electionTitle,
                          String electionLabel, String campus, String department,
                          Instant votedAt, List<CandidateVoteDTO> candidates) {
        this.id = id;
        this.type = type;
        this.votingType = votingType;
        this.electionTitle = electionTitle;
        this.electionLabel = electionLabel;
        this.campus = campus;
        this.department = department;
        this.votedAt = votedAt;
        this.candidates = candidates;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getVotingType() { return votingType; }
    public String getElectionTitle() { return electionTitle; }
    public String getElectionLabel() { return electionLabel; }
    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }
    public String getDepartment() { return department; }
    public Instant getVotedAt() { return votedAt; }
    public List<CandidateVoteDTO> getCandidates() { return candidates; }
}