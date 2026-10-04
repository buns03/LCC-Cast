package lccast.voting.system.dto.student;

import java.util.List;
import java.util.UUID;

public class PartylistCandidatesResponse {

    private UUID id;
    private String name;
    private String posterImageUrl;
    private String logoImageUrl;
    private List<PartylistMemberResponse> members;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPosterImageUrl() {
        return posterImageUrl;
    }

    public void setPosterImageUrl(String posterImageUrl) {
        this.posterImageUrl = posterImageUrl;
    }

    public String getLogoImageUrl() {
        return logoImageUrl;
    }

    public void setLogoImageUrl(String logoImageUrl) {
        this.logoImageUrl = logoImageUrl;
    }

    public List<PartylistMemberResponse> getMembers() {
        return members;
    }

    public void setMembers(List<PartylistMemberResponse> members) {
        this.members = members;
    }
}