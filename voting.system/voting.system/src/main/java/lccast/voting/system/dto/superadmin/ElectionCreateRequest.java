package lccast.voting.system.dto.superadmin;

import lccast.voting.system.model.ElectionCategory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ElectionCreateRequest {

    private String title;
    private ElectionCategory category;
    private UUID campusId;
    private String schoolYear;
    private Instant startAt;
    private Instant endAt;
    private UUID createdBy;

    private List<UUID> partylistIds;
    private List<UUID> departmentIds;

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

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public List<UUID> getPartylistIds() {
        return partylistIds;
    }

    public void setPartylistIds(List<UUID> partylistIds) {
        this.partylistIds = partylistIds;
    }

    public List<UUID> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(List<UUID> departmentIds) {
        this.departmentIds = departmentIds;
    }

}