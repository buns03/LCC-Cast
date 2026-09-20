package lccast.voting.system.dto;

public class CandidateVoteDTO {

    private String position;
    private String candidateName;
    private String affiliationName; // partylist name OR department name
    private String image;

    public CandidateVoteDTO(String position, String candidateName, String affiliationName, String image) {
        this.position = position;
        this.candidateName = candidateName;
        this.affiliationName = affiliationName;
        this.image = image;
    }

    public String getPosition() { return position; }
    public String getCandidateName() { return candidateName; }
    public String getAffiliationName() { return affiliationName; }
    public String getImage() { return image; }
}