package lccast.voting.system.service.student;

import lccast.voting.system.dto.student.CandidateResponse;
import lccast.voting.system.dto.student.PartylistCandidatesResponse;
import lccast.voting.system.dto.student.PartylistMemberResponse;
import lccast.voting.system.dto.student.PositionResponse;
import lccast.voting.system.dto.student.SscElectionResponse;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
        if (election.getStartAt() == null || election.getEndAt() == null) return ElectionPhase.UNSCHEDULED;
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
                .filter(e -> {
                    ElectionPhase p = computePhase(e);
                    return p == ElectionPhase.UPCOMING || p == ElectionPhase.ONGOING;
                })
                .min(java.util.Comparator
                        .comparing((Election e) -> computePhase(e) == ElectionPhase.ONGOING ? 0 : 1)
                        .thenComparing(Election::getStartAt));

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

        Map<UUID, String> partylistNames = loadPartylistNames(candidates);

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
                                    .map(c -> toCandidateResponse(c, partylistNames))
                                    .toList()
                    );
                    return positionResponse;
                })
                .toList();

        response.setPositions(positions);

        // Used by the "View Candidates" modal — groups the same
        // candidate list by partylist instead of by position, and
        // attaches each partylist's poster/logo.
        response.setPartylists(buildPartylistBreakdown(candidates));

        return response;
    }

// =====================================================
// MAP A SINGLE CANDIDATE
// =====================================================

    private CandidateResponse toCandidateResponse(Candidate candidate, Map<UUID, String> partylistNames) {

        CandidateResponse response = new CandidateResponse();

        response.setId(candidate.getId());

        String fullName = candidate.getFirstName()
                + (candidate.getMiddleName() != null && !candidate.getMiddleName().isBlank()
                ? " " + candidate.getMiddleName()
                : "")
                + " " + candidate.getLastName();

        response.setFullName(fullName.trim());

        String partylistName = candidate.getPartylistId() != null
                ? partylistNames.get(candidate.getPartylistId())
                : null;

        response.setPartylistName(partylistName);
        response.setPhotoImageUrl(candidate.getPhotoImageUrl());
        response.setCampaignImageUrl(candidate.getCampaignImageUrl());
        response.setBackgroundImageUrl(candidate.getBackgroundImageUrl());

        return response;
    }

    // =====================================================
    // BUILD PARTYLIST -> MEMBERS BREAKDOWN (for View Candidates)
    // =====================================================

    private List<PartylistCandidatesResponse> buildPartylistBreakdown(List<Candidate> candidates) {

        Map<UUID, List<Candidate>> byPartylist = candidates.stream()
                .filter(candidate -> candidate.getPartylistId() != null)
                .collect(Collectors.groupingBy(
                        Candidate::getPartylistId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        if (byPartylist.isEmpty()) return List.of();

        Map<UUID, Partylist> partylistsById = partylistRepository.findAllById(byPartylist.keySet())
                .stream()
                .collect(Collectors.toMap(Partylist::getId, p -> p));

        return byPartylist.entrySet().stream()
                .map(entry -> {
                    UUID partylistId = entry.getKey();
                    Partylist partylist = partylistsById.get(partylistId);

                    PartylistCandidatesResponse response = new PartylistCandidatesResponse();
                    response.setId(partylistId);
                    response.setName(partylist != null ? partylist.getName() : "Partylist");
                    response.setPosterImageUrl(partylist != null ? partylist.getPosterImageUrl() : null);
                    response.setLogoImageUrl(partylist != null ? partylist.getPosterLogoUrl() : null);
                    response.setMembers(
                            entry.getValue().stream()
                                    .map(this::toPartylistMemberResponse)
                                    .toList()
                    );
                    return response;
                })
                .toList();
    }

    private PartylistMemberResponse toPartylistMemberResponse(Candidate candidate) {

        PartylistMemberResponse response = new PartylistMemberResponse();

        String fullName = candidate.getFirstName()
                + (candidate.getMiddleName() != null && !candidate.getMiddleName().isBlank()
                ? " " + candidate.getMiddleName()
                : "")
                + " " + candidate.getLastName();

        response.setPosition(candidate.getPosition());
        response.setName(fullName.trim());
        response.setPhotoImageUrl(candidate.getPhotoImageUrl());
        response.setCampaignImageUrl(candidate.getCampaignImageUrl());
        response.setBackgroundImageUrl(candidate.getBackgroundImageUrl());

        return response;
    }

    private Map<UUID, String> loadPartylistNames(List<Candidate> candidates) {
        Set<UUID> ids = candidates.stream()
                .map(Candidate::getPartylistId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) return Map.of();

        return partylistRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Partylist::getId, Partylist::getName));
    }
}