package lccast.voting.system.dto.student;

import java.time.Instant;
import java.util.UUID;

public interface VoteSummaryRow {
    UUID getBallotId();
    String getElectionCategory();
    String getElectionTitle();
    String getSchoolYear();
    String getCampusName();
    String getDepartmentName();
    String getVotingType();
    Instant getVotedAt();
    String getPosition();
    String getCandidateName();
    String getPartylistName();
    String getPhotoImageUrl();
}