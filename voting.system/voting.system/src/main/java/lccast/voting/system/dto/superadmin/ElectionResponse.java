package lccast.voting.system.dto.superadmin;

import lccast.voting.system.model.ElectionCategory;
import lccast.voting.system.model.RecordStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ElectionResponse {

    private UUID id;
    private String title;
    private ElectionCategory category;
    private UUID campusId;
    private String campusName;
    private String schoolYear;
    private Instant startAt;
    private Instant endAt;
    private RecordStatus status;
    private boolean active;

    private List<UUID> partylistIds;
    private List<String> partylistNames;

    private List<UUID> departmentIds;
    private List<String> departmentNames;

    private String description;

    private UUID parentElectionId;
    private boolean drawElection;
    private List<String> drawPositions;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ElectionCategory getCategory() {
        return category;
    }

    public void setCategory(ElectionCategory category) {
        this.category = category;
    }

    public UUID getCampusId() {
        return campusId;
    }

    public void setCampusId(UUID campusId) {
        this.campusId = campusId;
    }

    public String getCampusName() {
        return campusName;
    }

    public void setCampusName(String campusName) {
        this.campusName = campusName;
    }

    public String getSchoolYear() {
        return schoolYear;
    }

    public void setSchoolYear(String schoolYear) {
        this.schoolYear = schoolYear;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<UUID> getPartylistIds() {
        return partylistIds;
    }

    public void setPartylistIds(List<UUID> partylistIds) {
        this.partylistIds = partylistIds;
    }

    public List<String> getPartylistNames() {
        return partylistNames;
    }

    public void setPartylistNames(List<String> partylistNames) {
        this.partylistNames = partylistNames;
    }

    public List<UUID> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(List<UUID> departmentIds) {
        this.departmentIds = departmentIds;
    }

    public List<String> getDepartmentNames() {
        return departmentNames;
    }

    public void setDepartmentNames(List<String> departmentNames) {
        this.departmentNames = departmentNames;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getParentElectionId() { return parentElectionId; }
    public void setParentElectionId(UUID v) { this.parentElectionId = v; }
    public boolean isDrawElection() { return drawElection; }
    public void setDrawElection(boolean v) { this.drawElection = v; }
    public List<String> getDrawPositions() { return drawPositions; }
    public void setDrawPositions(List<String> v) { this.drawPositions = v; }
}