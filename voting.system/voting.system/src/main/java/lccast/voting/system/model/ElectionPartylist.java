package lccast.voting.system.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
        name = "election_partylists",
        schema = "public",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"election_id", "partylist_id"})
        }
)
public class ElectionPartylist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partylist_id", nullable = false)
    private Partylist partylist;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Election getElection() {
        return election;
    }

    public void setElection(Election election) {
        this.election = election;
    }

    public Partylist getPartylist() {
        return partylist;
    }

    public void setPartylist(Partylist partylist) {
        this.partylist = partylist;
    }
}