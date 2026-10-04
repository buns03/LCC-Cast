package lccast.voting.system.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "elections", schema = "public")
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "category",
            nullable = false,
            columnDefinition = "election_category"
    )
    private ElectionCategory category;

    @Column(name = "campus_id", nullable = false)
    private UUID campusId;

    @Column(name = "school_year", nullable = false)
    private String schoolYear;

    @Column(name = "start_at")            // was nullable = false
    private Instant startAt;

    @Column(name = "end_at")              // was nullable = false
    private Instant endAt;



    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "record_status"
    )
    private RecordStatus status;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "election",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ElectionDepartment> electionDepartments = new ArrayList<>();

    @OneToMany(
            mappedBy = "election",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ElectionPartylist> electionPartylists = new ArrayList<>();

    @Column(name = "parent_election_id")
    private UUID parentElectionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draw_positions", columnDefinition = "jsonb")
    private List<String> drawPositions;

    @Column(name = "draw_checked_at")
    private Instant drawCheckedAt;

    public boolean isDrawElection() { return parentElectionId != null; }

    // Election.java — add import: java.time.Instant, java.time.temporal.ChronoUnit

    public ElectionPhase getPhase() {
        if (startAt == null || endAt == null) return ElectionPhase.UNSCHEDULED;
        Instant now = Instant.now();

        if (now.isAfter(endAt)) {
            return ElectionPhase.CONCLUDED;
        }
        if (now.isBefore(startAt)) {
            long daysUntilStart = ChronoUnit.DAYS.between(now, startAt);
            return daysUntilStart <= 5
                    ? ElectionPhase.UPCOMING
                    : ElectionPhase.NOT_VISIBLE;
        }
        return ElectionPhase.ONGOING;
    }

    // =========================
    // GETTERS AND SETTERS
    // =========================

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

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ElectionDepartment> getElectionDepartments() {
        return electionDepartments;
    }

    public void setElectionDepartments(List<ElectionDepartment> electionDepartments) {
        this.electionDepartments = electionDepartments;
    }

    public List<ElectionPartylist> getElectionPartylists() {
        return electionPartylists;
    }

    public void setElectionPartylists(List<ElectionPartylist> electionPartylists) {
        this.electionPartylists = electionPartylists;
    }

    public UUID getParentElectionId() { return parentElectionId; }
    public void setParentElectionId(UUID v) { this.parentElectionId = v; }
    public List<String> getDrawPositions() { return drawPositions; }
    public void setDrawPositions(List<String> v) { this.drawPositions = v; }
    public Instant getDrawCheckedAt() { return drawCheckedAt; }
    public void setDrawCheckedAt(Instant v) { this.drawCheckedAt = v; }
}