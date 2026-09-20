package lccast.voting.system.service.student;

import lccast.voting.system.dto.student.CandidateResponse;
import lccast.voting.system.dto.student.PositionResponse;
import lccast.voting.system.dto.student.SscElectionResponse;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Service
public class SscElectionService {

    private static final long UPCOMING_VISIBILITY_DAYS = 5;

    private final ElectionRepository electionRepository;
    private final VoterRepository voterRepository;
    private final CandidateRepository candidateRepository;
    private final BallotRepository ballotRepository;
    private final PartylistRepository partylistRepository;

    public SscElectionService(
            ElectionRepository electionRepository,
            VoterRepository voterRepository,
            CandidateRepository candidateRepository,
            BallotRepository ballotRepository,
            PartylistRepository partylistRepository
    ) {
        this.electionRepository = electionRepository;
        this.voterRepository = voterRepository;
        this.candidateRepository = candidateRepository;
        this.ballotRepository = ballotRepository;
        this.partylistRepository = partylistRepository;
    }

    // =====================================================
    // PHASE COMPUTATION
    // =====================================================

    public ElectionPhase computePhase(Election election) {
        Instant now = Instant.now();

        Instant visibleFrom = election.getStartAt()
                .minus(UPCOMING_VISIBILITY_DAYS, ChronoUnit.DAYS);

        if (now.isBefore(visibleFrom)) {
            return ElectionPhase.NOT_VISIBLE;
        }

        if (now.isBefore(election.getStartAt())) {
            return ElectionPhase.UPCOMING;
        }

        if (!now.isAfter(election.getEndAt())) {
            return ElectionPhase.ONGOING;
        }

        return ElectionPhase.CONCLUDED;
    }

    // =====================================================
    // FIND THE RELEVANT ELECTION FOR THIS VOTER
    // =====================================================

    public Optional<Election> findRelevantSscElection(Voter voter) {
        List<Election> candidates = electionRepository
                .findByCampusIdAndCategoryAndStatus(
                        voter.getCampusId(),
                        ElectionCategory.SSC,
                        RecordStatus.ACTIVE
                );

        // Prefer an UPCOMING or ONGOING election if one exists.
        Optional<Election> active = candidates.stream()
                .filter(election -> {
                    ElectionPhase phase = computePhase(election);
                    return phase == ElectionPhase.UPCOMING
                            || phase == ElectionPhase.ONGOING;
                })
                .findFirst();

        if (active.isPresent()) {
            return active;
        }

        // Otherwise fall back to the most recently concluded election,
        // so voters see "voting closed on <endAt>" instead of "no election".
        return candidates.stream()
                .filter(election -> computePhase(election) == ElectionPhase.CONCLUDED)
                .max(java.util.Comparator.comparing(Election::getEndAt));
    }

    // =====================================================
    // RESOLVE VOTER FROM SESSION userId
    // =====================================================

    public Voter resolveVoter(UUID authUserId) {
        return voterRepository.findByAuthUserId(authUserId)
                .orElseThrow(() ->
                        new RuntimeException("Voter record not found.")
                );
    }

    public SscElectionResponse buildResponse(Voter voter) {

        SscElectionResponse response = new SscElectionResponse();

        Optional<Election> maybeElection = findRelevantSscElection(voter);

        if (maybeElection.isEmpty()) {
            response.setFound(false);
            return response;
        }

        Election election = maybeElection.get();
        ElectionPhase phase = computePhase(election);

        response.setFound(true);
        response.setElectionId(election.getId());
        response.setTitle(election.getTitle());
        response.setSchoolYear(election.getSchoolYear());
        response.setStartAt(election.getStartAt());
        response.setEndAt(election.getEndAt());
        response.setPhase(phase);

        boolean hasVoted = ballotRepository.existsByElection_IdAndVoter_Id(
                election.getId(),
                voter.getId()
        );
        response.setHasVoted(hasVoted);

        List<Candidate> candidates =
                candidateRepository.findByElectionId(election.getId());

        Map<String, List<Candidate>> grouped = candidates.stream()
                .collect(Collectors.groupingBy(
                        Candidate::getPosition,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<PositionResponse> positions = grouped.entrySet().stream()
                .map(entry -> {
                    PositionResponse positionResponse = new PositionResponse();
                    positionResponse.setName(entry.getKey());
                    positionResponse.setCandidates(
                            entry.getValue().stream()
                                    .map(this::toCandidateResponse)
                                    .toList()
                    );
                    return positionResponse;
                })
                .toList();

        response.setPositions(positions);

        return response;
    }

// =====================================================
// MAP A SINGLE CANDIDATE
// =====================================================

    private CandidateResponse toCandidateResponse(Candidate candidate) {

        CandidateResponse response = new CandidateResponse();

        response.setId(candidate.getId());

        String fullName = candidate.getFirstName()
                + (candidate.getMiddleName() != null && !candidate.getMiddleName().isBlank()
                ? " " + candidate.getMiddleName()
                : "")
                + " " + candidate.getLastName();

        response.setFullName(fullName.trim());

        String partylistName = null;

        if (candidate.getPartylistId() != null) {
            partylistName = partylistRepository.findById(candidate.getPartylistId())
                    .map(Partylist::getName)
                    .orElse(null);
        }

        response.setPartylistName(partylistName);
        response.setPhotoImageUrl(candidate.getPhotoImageUrl());
        response.setCampaignImageUrl(candidate.getCampaignImageUrl());
        response.setBackgroundImageUrl(candidate.getBackgroundImageUrl());

        return response;
    }
}