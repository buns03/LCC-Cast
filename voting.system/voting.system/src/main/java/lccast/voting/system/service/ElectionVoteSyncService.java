package lccast.voting.system.service;

import lccast.voting.system.event.VoteCastEvent;
import lccast.voting.system.model.Ballot;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.model.VotingStatus;
import lccast.voting.system.repository.BallotRepository;
import lccast.voting.system.repository.VoterRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps voters.voting_status / time_voted in line with their ballots in ACTIVE elections.
 * Ballots of archived or deleted elections are kept but do not count.
 * Voter records themselves are never touched.
 */
@Service
public class ElectionVoteSyncService {

    private final BallotRepository ballotRepository;
    private final VoterRepository voterRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ElectionVoteSyncService(
            BallotRepository ballotRepository,
            VoterRepository voterRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.ballotRepository = ballotRepository;
        this.voterRepository = voterRepository;
        this.eventPublisher = eventPublisher;
    }

    public Set<UUID> voterIdsOf(UUID electionId) {
        return ballotRepository.findByElection_IdOrderBySubmittedAtAsc(electionId).stream()
                .map(ballot -> ballot.getVoter().getId())
                .collect(Collectors.toSet());
    }

    /** Call after an election is archived, deleted or restored. */
    @Transactional
    public void refreshVotersOfElection(UUID electionId) {
        refreshVoters(voterIdsOf(electionId));
    }

    @Transactional
    public void refreshVoters(Collection<UUID> voterIds) {

        for (UUID voterId : voterIds) {

            voterRepository.findById(voterId).ifPresent(voter -> {

                Optional<Ballot> latestActive = ballotRepository
                        .findTopByVoter_IdAndElection_StatusOrderBySubmittedAtDesc(
                                voterId, RecordStatus.ACTIVE
                        );

                if (latestActive.isPresent()) {
                    voter.setVotingStatus(VotingStatus.VOTED);
                    voter.setTimeVoted(latestActive.get().getSubmittedAt());
                } else {
                    voter.setVotingStatus(VotingStatus.NOT_VOTED);
                    voter.setTimeVoted(null);
                }

                voter.setUpdatedAt(Instant.now());
                voterRepository.save(voter);
            });
        }

        // Same event vote submission publishes, so analytics / live results refresh
        eventPublisher.publishEvent(new VoteCastEvent(this));
    }
}