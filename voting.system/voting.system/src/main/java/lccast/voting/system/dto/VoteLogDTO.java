package lccast.voting.system.dto;

import lccast.voting.system.model.VoteLog;

import java.time.Instant;
import java.util.UUID;

public class VoteLogDTO {

    private UUID id;
    private String studentId;
    private String fullName;
    private String email;
    private String initials;
    private String programCourse;
    private String section;
    private String yearLevel;
    private UUID campusId;
    private String campusName;   // populate via join/lookup if available, else null
    private UUID electionId;
    private String electionName; // populate via join/lookup if available, else null
    private String voteStatus;
    private Instant votedAt;
    private UUID ballotId;
    private String electionCategory;
    public String getElectionCategory() { return electionCategory; }
    public void setElectionCategory(String electionCategory) { this.electionCategory = electionCategory; }

    public static VoteLogDTO from(VoteLog vl) {
        VoteLogDTO dto = new VoteLogDTO();
        dto.id = vl.getId();
        dto.studentId = vl.getStudentId();
        dto.fullName = vl.getFullName();
        dto.email = vl.getEmail();
        dto.initials = initialsOf(vl.getFullName());
        dto.programCourse = vl.getProgramCourse();
        dto.section = vl.getSection();
        dto.yearLevel = vl.getYearLevel();
        dto.campusId = vl.getCampusId();
        // CHANGE these two lines:
        dto.campusName = vl.getCampusName();   // CHANGE     // was never set at all before — add it
        dto.electionName = vl.getElectionName();   // was: not set / TODO
        dto.electionId = vl.getElectionId();
        dto.voteStatus = vl.getVoteStatus() != null ? vl.getVoteStatus().name() : null;
        dto.votedAt = vl.getVotedAt();
        dto.ballotId = vl.getBallotId();
        return dto;
    }

    private static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "??";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts.length > 0 ? parts[0].substring(0, 1) : "";
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    // getters + setters (needed for Jackson serialization)

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getInitials() { return initials; }
    public void setInitials(String initials) { this.initials = initials; }

    public String getProgramCourse() { return programCourse; }
    public void setProgramCourse(String programCourse) { this.programCourse = programCourse; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getYearLevel() { return yearLevel; }
    public void setYearLevel(String yearLevel) { this.yearLevel = yearLevel; }

    public UUID getCampusId() { return campusId; }
    public void setCampusId(UUID campusId) { this.campusId = campusId; }

    public String getCampusName() { return campusName; }
    public void setCampusName(String campusName) { this.campusName = campusName; }

    public UUID getElectionId() { return electionId; }
    public void setElectionId(UUID electionId) { this.electionId = electionId; }

    public String getElectionName() { return electionName; }
    public void setElectionName(String electionName) { this.electionName = electionName; }

    public String getVoteStatus() { return voteStatus; }
    public void setVoteStatus(String voteStatus) { this.voteStatus = voteStatus; }

    public Instant getVotedAt() { return votedAt; }
    public void setVotedAt(Instant votedAt) { this.votedAt = votedAt; }

    public UUID getBallotId() {
        return ballotId;
    }

    public void setBallotId(UUID ballotId) {
        this.ballotId = ballotId;
    }
}