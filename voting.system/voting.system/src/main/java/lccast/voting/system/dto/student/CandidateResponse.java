package lccast.voting.system.dto.student;

import java.util.UUID;

public class CandidateResponse {

    private UUID id;
    private String fullName;
    private String partylistName;
    private String photoImageUrl;
    private String campaignImageUrl;
    private String backgroundImageUrl;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPartylistName() { return partylistName; }
    public void setPartylistName(String partylistName) { this.partylistName = partylistName; }

    public String getPhotoImageUrl() { return photoImageUrl; }
    public void setPhotoImageUrl(String photoImageUrl) { this.photoImageUrl = photoImageUrl; }

    public String getCampaignImageUrl() { return campaignImageUrl; }
    public void setCampaignImageUrl(String campaignImageUrl) { this.campaignImageUrl = campaignImageUrl; }

    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }
}