package lccast.voting.system.dto;

import java.util.UUID;

public class CandidateResultDTO {
    private UUID id;
    private String name;
    private String photo;
    private String partylist;
    private long votes;

    public CandidateResultDTO(UUID id, String name, String photo, String partylist, long votes) {
        this.id = id;
        this.name = name;
        this.photo = photo;
        this.partylist = partylist;
        this.votes = votes;
    }

    // getters only — this is a read model, no setters needed
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getPhoto() { return photo; }
    public String getPartylist() { return partylist; }
    public long getVotes() { return votes; }
}