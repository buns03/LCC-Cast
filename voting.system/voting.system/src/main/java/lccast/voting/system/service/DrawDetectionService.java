package lccast.voting.system.service;

import lccast.voting.system.model.*;
import lccast.voting.system.repository.BallotVoteRepository;
import lccast.voting.system.repository.CandidateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DrawDetectionService {

    public static final String DRAW_MESSAGE = "Draw — no official winner yet.";

    /** tiedByPosition: position -> candidates sharing the top vote count.
     *  winnersByPosition: only filled for REPRESENTATIVE elections. */
    public record DrawOutcome(Map<String, List<UUID>> tiedByPosition,
                              Map<String, UUID> winnersByPosition) {
        public static DrawOutcome none() { return new DrawOutcome(Map.of(), Map.of()); }
        public boolean hasDraw() { return !tiedByPosition.isEmpty(); }
        public Set<String> positions() { return tiedByPosition.keySet(); }
    }

    private final CandidateRepository candidateRepository;
    private final BallotVoteRepository ballotVoteRepository;

    public DrawDetectionService(CandidateRepository candidateRepository,
                                BallotVoteRepository ballotVoteRepository) {
        this.candidateRepository = candidateRepository;
        this.ballotVoteRepository = ballotVoteRepository;
    }

    @Transactional(readOnly = true)
    public DrawOutcome detect(Election election) {
        if (election.getPhase() != ElectionPhase.CONCLUDED) return DrawOutcome.none();

        if (election.getCategory() == ElectionCategory.DEPARTMENT) {
            List<Department> depts = election.getElectionDepartments().stream()
                    .map(ElectionDepartment::getDepartment).toList();
            if (!depts.isEmpty() && depts.get(0).getVotingType() == VotingType.REPRESENTATIVE) {
                return detectRepresentative(election, depts.get(0));
            }
        }
        return detectByCandidatePosition(election);   // SSC + PARTYLIST-type department
    }

    /** Positions still "open" in this election (a draw election only settles its draw positions). */
    public List<String> openPositions(Election election, Department dept) {
        List<String> all = dept.getPositions() != null ? dept.getPositions() : List.of();
        List<String> drawPositions = election.getDrawPositions();
        if (drawPositions == null || drawPositions.isEmpty()) return all;
        return all.stream().filter(drawPositions::contains).toList();
    }

    // ---------- SSC / PARTYLIST: candidate.position decides the race ----------
    private DrawOutcome detectByCandidatePosition(Election election) {
        Map<UUID, Long> counts = ballotVoteRepository
                .countVotesByCandidateForElection(election.getId()).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        Map<String, List<Candidate>> byPosition = candidateRepository
                .findByElectionId(election.getId()).stream()
                .filter(c -> c.getPosition() != null)
                .collect(Collectors.groupingBy(Candidate::getPosition, LinkedHashMap::new, Collectors.toList()));

        Map<String, List<UUID>> tied = new LinkedHashMap<>();
        byPosition.forEach((position, group) -> {
            if (group.size() < 2) return;                       // unopposed
            long top = group.stream().mapToLong(c -> counts.getOrDefault(c.getId(), 0L)).max().orElse(0L);
            if (top == 0) return;                               // nobody voted for this position
            List<UUID> leaders = group.stream()
                    .filter(c -> counts.getOrDefault(c.getId(), 0L) == top)
                    .map(Candidate::getId).toList();
            if (leaders.size() > 1) tied.put(position, leaders);
        });
        return new DrawOutcome(tied, Map.of());
    }

    // ---------- REPRESENTATIVE: position lives on ballot_votes.position ----------
    private DrawOutcome detectRepresentative(Election election, Department dept) {
        record Pair(UUID candidateId, String position, long votes) {}

        List<String> open = openPositions(election, dept);

        List<Pair> pairs = ballotVoteRepository
                .countVotesByCandidateAndPositionForElectionAndDepartment(election.getId(), dept.getId())
                .stream()
                .map(r -> new Pair((UUID) r[0], (String) r[1], (Long) r[2]))
                .filter(p -> p.position() != null && open.contains(p.position()))
                .toList();

        Set<UUID> setAside = new HashSet<>();   // candidates already placed or tied
        Set<String> closed = new HashSet<>();   // positions already won or tied
        Map<String, List<UUID>> tied = new LinkedHashMap<>();
        Map<String, UUID> winners = new LinkedHashMap<>();

        // Same greedy idea as the live results (highest vote first, one seat per candidate),
        // but a tie for the top spot in a position is recorded as a draw instead of picking one.
        while (true) {
            List<Pair> available = pairs.stream()
                    .filter(p -> !setAside.contains(p.candidateId()) && !closed.contains(p.position()))
                    .toList();
            if (available.isEmpty()) break;

            long top = available.stream().mapToLong(Pair::votes).max().getAsLong();
            Map<String, List<Pair>> leaders = available.stream()
                    .filter(p -> p.votes() == top)
                    .collect(Collectors.groupingBy(Pair::position, LinkedHashMap::new, Collectors.toList()));

            leaders.forEach((position, group) -> {              // ties first
                if (group.size() > 1) {
                    tied.put(position, group.stream().map(Pair::candidateId).toList());
                    closed.add(position);
                    group.forEach(g -> setAside.add(g.candidateId()));
                }
            });
            leaders.forEach((position, group) -> {              // then clear winners
                if (group.size() == 1 && !closed.contains(position)
                        && !setAside.contains(group.get(0).candidateId())) {
                    winners.put(position, group.get(0).candidateId());
                    closed.add(position);
                    setAside.add(group.get(0).candidateId());
                }
            });
        }
        return new DrawOutcome(tied, winners);
    }
}