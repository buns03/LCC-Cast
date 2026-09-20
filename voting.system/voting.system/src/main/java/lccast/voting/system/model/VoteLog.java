package lccast.voting.system.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vote_logs", schema = "public")
public class VoteLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    @Column(name = "voter_id", nullable = false)
    private UUID voterId;

    @Column(name = "ballot_id", nullable = false)
    private UUID ballotId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "program_course")
    private String programCourse;

    @Column(name = "year_level")
    private String yearLevel;

    @Column(name = "campus_id")
    private UUID campusId;

    @Column(name = "section")
    private String section;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "vote_status",
            nullable = false,
            columnDefinition = "voting_status"
    )
    private VotingStatus voteStatus;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    @Column(name = "election_category")
    private String electionCategory;

    @Column(name = "election_name")
    private String electionName;

    @Column(name = "campus_name")
    private String campusName;


    // =========================
    // GETTERS AND SETTERS
    // =========================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getElectionId() {
        return electionId;
    }

    public void setElectionId(UUID electionId) {
        this.electionId = electionId;
    }

    public UUID getVoterId() {
        return voterId;
    }

    public void setVoterId(UUID voterId) {
        this.voterId = voterId;
    }

    public UUID getBallotId() {
        return ballotId;
    }

    public void setBallotId(UUID ballotId) {
        this.ballotId = ballotId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProgramCourse() {
        return programCourse;
    }

    public void setProgramCourse(String programCourse) {
        this.programCourse = programCourse;
    }

    public String getYearLevel() {
        return yearLevel;
    }

    public void setYearLevel(String yearLevel) {
        this.yearLevel = yearLevel;
    }

    public UUID getCampusId() {
        return campusId;
    }

    public void setCampusId(UUID campusId) {
        this.campusId = campusId;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public VotingStatus getVoteStatus() {
        return voteStatus;
    }

    public void setVoteStatus(VotingStatus voteStatus) {
        this.voteStatus = voteStatus;
    }

    public Instant getVotedAt() {
        return votedAt;
    }

    public void setVotedAt(Instant votedAt) {
        this.votedAt = votedAt;
    }

    public String getElectionCategory() {
        return electionCategory;
    }

    public void setElectionCategory(String electionCategory) {
        this.electionCategory = electionCategory;
    }

    public String getElectionName() {
        return electionName;
    }

    public void setElectionName(String electionName) {
        this.electionName = electionName;
    }

    public String getCampusName() {
        return campusName;
    }

    public void setCampusName(String campusName) {
        this.campusName = campusName;
    }
}