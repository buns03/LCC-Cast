package lccast.voting.system.dto.student;

public class PartylistMemberResponse {

    private String position;
    private String name;
    private String photoImageUrl;
    private String campaignImageUrl;
    private String backgroundImageUrl;

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhotoImageUrl() {
        return photoImageUrl;
    }

    public void setPhotoImageUrl(String photoImageUrl) {
        this.photoImageUrl = photoImageUrl;
    }

    public String getCampaignImageUrl() {
        return campaignImageUrl;
    }

    public void setCampaignImageUrl(String campaignImageUrl) {
        this.campaignImageUrl = campaignImageUrl;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl = backgroundImageUrl;
    }
}