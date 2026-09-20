package lccast.voting.system.dto;

import lccast.voting.system.model.ElectionPhase;
import lccast.voting.system.model.VotingType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EntityResultDTO {

    private String id;
    private String key;
    private String name;
    private String title;

    private ElectionPhase phase;
    private VotingType votingType;

    private long votesCast;

    private Instant startAt;
    private Instant endAt;

    private Map<String, List<CandidateResultDTO>> positions;

    private String campusId;
    private String campusName;
    private String departmentId;
    private List<String> departmentIds = new ArrayList<>();

    public EntityResultDTO(
            String id,
            String key,
            String name,
            String title,
            ElectionPhase phase,
            VotingType votingType,
            long votesCast,
            Instant startAt,
            Instant endAt,
            Map<String, List<CandidateResultDTO>> positions,
            String campusId,
            String campusName,
            String departmentId
    ) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.title = title;
        this.phase = phase;
        this.votingType = votingType;
        this.votesCast = votesCast;
        this.startAt = startAt;
        this.endAt = endAt;
        this.positions = positions;
        this.campusId = campusId;
        this.campusName = campusName;
        this.departmentId = departmentId;
    }

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public ElectionPhase getPhase() {
        return phase;
    }

    public VotingType getVotingType() {
        return votingType;
    }

    public long getVotesCast() {
        return votesCast;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Map<String, List<CandidateResultDTO>> getPositions() {
        return positions;
    }

    public String getCampusId() {
        return campusId;
    }

    public String getCampusName() {
        return campusName;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public List<String> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(List<String> departmentIds) {
        this.departmentIds = departmentIds;
    }
}