package lccast.voting.system.dto.student;

import lccast.voting.system.model.ElectionPhase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SscElectionResponse {

    private boolean found;
    private UUID electionId;
    private String title;
    private String schoolYear;
    private Instant startAt;
    private Instant endAt;
    private ElectionPhase phase;
    private boolean hasVoted;
    private List<PositionResponse> positions;

    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }

    public UUID getElectionId() { return electionId; }
    public void setElectionId(UUID electionId) { this.electionId = electionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSchoolYear() { return schoolYear; }
    public void setSchoolYear(String schoolYear) { this.schoolYear = schoolYear; }

    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }

    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }

    public ElectionPhase getPhase() { return phase; }
    public void setPhase(ElectionPhase phase) { this.phase = phase; }

    public boolean isHasVoted() { return hasVoted; }
    public void setHasVoted(boolean hasVoted) { this.hasVoted = hasVoted; }

    public List<PositionResponse> getPositions() { return positions; }
    public void setPositions(List<PositionResponse> positions) { this.positions = positions; }
}