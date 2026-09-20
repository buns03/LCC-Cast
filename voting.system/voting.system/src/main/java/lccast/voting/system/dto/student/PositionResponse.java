package lccast.voting.system.dto.student;

import java.util.List;

public class PositionResponse {

    private String name;
    private List<CandidateResponse> candidates;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<CandidateResponse> getCandidates() { return candidates; }
    public void setCandidates(List<CandidateResponse> candidates) { this.candidates = candidates; }
}