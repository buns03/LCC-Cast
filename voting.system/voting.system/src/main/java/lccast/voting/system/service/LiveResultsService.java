package lccast.voting.system.service;

import lccast.voting.system.dto.CandidateResultDTO;
import lccast.voting.system.dto.EntityResultDTO;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LiveResultsService {

    private final ElectionRepository electionRepository;
    private final CampusRepository campusRepository;
    private final CandidateRepository candidateRepository;
    private final BallotVoteRepository ballotVoteRepository;
    private final PartylistRepository partylistRepository;
    private final DrawDetectionService drawDetectionService;

    public LiveResultsService(
            ElectionRepository electionRepository,
            CampusRepository campusRepository,
            CandidateRepository candidateRepository,
            BallotVoteRepository ballotVoteRepository,
            PartylistRepository partylistRepository,
            DrawDetectionService drawDetectionService
    ) {
        this.electionRepository = electionRepository;
        this.campusRepository = campusRepository;
        this.candidateRepository = candidateRepository;
        this.ballotVoteRepository = ballotVoteRepository;
        this.partylistRepository = partylistRepository;
        this.drawDetectionService = drawDetectionService;
    }

    @Transactional(readOnly = true)
    public List<EntityResultDTO> getSscLiveResults() {

        List<Election> sscElections = electionRepository
                .findByCategoryAndStatus(ElectionCategory.SSC, RecordStatus.ACTIVE);

        List<EntityResultDTO> results = new ArrayList<>();

        for (Election election : sscElections) {

            ElectionPhase phase = election.getPhase();

            if (phase == ElectionPhase.NOT_VISIBLE) {
                continue;
            }

            Campus campus = campusRepository.findById(election.getCampusId())
                    .orElse(null);
            if (campus == null) continue;

            List<Candidate> candidates = candidateRepository.findByElectionId(election.getId());

            Map<UUID, Long> voteCounts = new HashMap<>();
            if (hasVotes(phase)) {                                   // was: phase != UPCOMING
                ballotVoteRepository.countVotesByCandidateForElection(election.getId())
                        .forEach(row -> voteCounts.put((UUID) row[0], (Long) row[1]));
            }

            Map<UUID, String> partylistNames = resolvePartylistNames(candidates);

            Map<String, List<CandidateResultDTO>> positions = buildPositionsMap(
                    candidates, voteCounts, false, partylistNames   // was: phase == ElectionPhase.ONGOING
            );

            long totalVotes = ballotVoteRepository
                    .countDistinctBallotsForElection(election.getId());

            DrawDetectionService.DrawOutcome draw = drawDetectionService.detect(election);

            EntityResultDTO dto = new EntityResultDTO(
                    election.getId().toString(), campus.getName(), election.getTitle(), election.getTitle(),
                    phase, null, totalVotes, election.getStartAt(), election.getEndAt(), positions,
                    campus.getId().toString(), campus.getName(), null);
            applyElectionMeta(dto, election, draw);
            results.add(dto);
        }

        return results;
    }

    private Map<String, List<CandidateResultDTO>> buildPositionsMap(
            List<Candidate> candidates,
            Map<UUID, Long> voteCounts,
            boolean anonymize,
            Map<UUID, String> partylistNamesById
    ) {
        Map<String, List<Candidate>> grouped = candidates.stream()
                .filter(c -> c.getPosition() != null)
                .collect(Collectors.groupingBy(Candidate::getPosition));

        Map<String, List<CandidateResultDTO>> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<Candidate>> entry : grouped.entrySet()) {

            List<Candidate> group = entry.getValue();

            group.sort((a, b) -> Long.compare(
                    voteCounts.getOrDefault(b.getId(), 0L),
                    voteCounts.getOrDefault(a.getId(), 0L)
            ));

            List<CandidateResultDTO> dtos = new ArrayList<>();
            int i = 0;
            for (Candidate c : group) {
                long votes = voteCounts.getOrDefault(c.getId(), 0L);

                if (anonymize) {
                    dtos.add(new CandidateResultDTO(
                            c.getId(),
                            "Candidate " + (i + 1),
                            null,
                            "Anonymous Partylist",
                            votes
                    ));
                } else {
                    dtos.add(new CandidateResultDTO(
                            c.getId(),
                            c.getFirstName() + " " + c.getLastName(),
                            c.getPhotoImageUrl(),
                            c.getPartylistId() != null
                                    ? partylistNamesById.get(c.getPartylistId())
                                    : null,
                            votes
                    ));
                }
                i++;
            }

            result.put(entry.getKey(), dtos);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<EntityResultDTO> getDepartmentLiveResults() {

        List<Election> deptElections = electionRepository
                .findByCategoryAndStatus(ElectionCategory.DEPARTMENT, RecordStatus.ACTIVE);

        List<EntityResultDTO> results = new ArrayList<>();

        for (Election election : deptElections) {

            List<Department> depts = election.getElectionDepartments().stream()
                    .map(ElectionDepartment::getDepartment)
                    .collect(Collectors.toList());
            if (depts.isEmpty()) continue;

            ElectionPhase phase = election.getPhase();
            if (phase == ElectionPhase.NOT_VISIBLE) continue;

            DrawDetectionService.DrawOutcome draw = drawDetectionService.detect(election);
            Department primaryDept = depts.get(0);
            Map<String, List<CandidateResultDTO>> positions;

            if (primaryDept.getVotingType() == VotingType.REPRESENTATIVE) {
                List<Candidate> repCandidates = candidateRepository
                        .findByElectionIdAndDepartmentId(election.getId(), primaryDept.getId());
                positions = buildRepresentativePositions(
                        election.getId(), primaryDept.getId(),
                        drawDetectionService.openPositions(election, primaryDept),   // draw elections only show their positions
                        phase, repCandidates, resolvePartylistNames(repCandidates), draw);
            } else {
                List<Candidate> candidates = candidateRepository.findByElectionId(election.getId());
                Map<UUID, Long> voteCounts = new HashMap<>();
                if (hasVotes(phase)) {
                    ballotVoteRepository.countVotesByCandidateForElection(election.getId())
                            .forEach(row -> voteCounts.put((UUID) row[0], (Long) row[1]));
                }
                Map<UUID, String> partylistNames = resolvePartylistNames(candidates);
                positions = buildPositionsMap(candidates, voteCounts, false, partylistNames);
            }

            long totalVotes = ballotVoteRepository.countDistinctBallotsForElection(election.getId());

            Campus campus = campusRepository.findById(election.getCampusId()).orElse(null);
            if (campus == null) continue;

            EntityResultDTO dto = new EntityResultDTO(
                    election.getId().toString(),
                    primaryDept.getName(),
                    primaryDept.getName(),
                    election.getTitle(),
                    phase,
                    primaryDept.getVotingType(),
                    totalVotes,
                    election.getStartAt(),
                    election.getEndAt(),
                    positions,
                    campus.getId().toString(),
                    campus.getName(),
                    primaryDept.getId().toString()
            );

            // An election can be shared by multiple departments/parties running
            // against each other for the same positions. Tag every attached
            // department's id so admin-dept scoping matches regardless of
            // which party happened to load first as "primary".
            dto.setDepartmentIds(depts.stream().map(d -> d.getId().toString()).toList());

            applyElectionMeta(dto, election, draw);

            results.add(dto);
        }

        return results;
    }

    private Map<String, List<CandidateResultDTO>> buildRepresentativePositions(
            UUID electionId, UUID departmentId, List<String> positions, ElectionPhase phase,
            List<Candidate> candidates, Map<UUID, String> partylistNamesById, DrawDetectionService.DrawOutcome draw) {

        if (!hasVotes(phase)) {                                // UPCOMING / UNSCHEDULED: just list the candidates
            Map<String, List<CandidateResultDTO>> upcoming = new LinkedHashMap<>();
            upcoming.put("Candidates", candidates.stream()
                    .map(c -> toDto(c, 0L, partylistNamesById)).collect(Collectors.toList()));
            return upcoming;
        }

        Map<UUID, Candidate> candidateById = candidates.stream()
                .collect(Collectors.toMap(Candidate::getId, c -> c));

        List<Object[]> rows = ballotVoteRepository
                .countVotesByCandidateAndPositionForElectionAndDepartment(electionId, departmentId);

        // ---- CONCLUDED with a draw: DetectionService already resolved winners + ties ----
        if (draw.hasDraw()) {
            Map<String, Long> votes = new HashMap<>();
            for (Object[] r : rows) votes.put(r[0] + "|" + r[1], (Long) r[2]);

            Map<String, List<CandidateResultDTO>> result = new LinkedHashMap<>();
            for (String position : positions) {
                List<UUID> tied = draw.tiedByPosition().get(position);
                if (tied != null) {                           // draw: show every tied candidate, no single winner
                    result.put(position, tied.stream()
                            .map(id -> toDto(candidateById.get(id), votes.getOrDefault(id + "|" + position, 0L), partylistNamesById))
                            .collect(Collectors.toList()));
                } else if (draw.winnersByPosition().get(position) != null) {
                    UUID id = draw.winnersByPosition().get(position);
                    result.put(position, new ArrayList<>(List.of(
                            toDto(candidateById.get(id), votes.getOrDefault(id + "|" + position, 0L), partylistNamesById))));
                }
            }
            return result;
        }

        record Pairing(UUID candidateId, String position, long votes) {}

        List<Pairing> pairings = rows.stream()
                .map(r -> new Pairing((UUID) r[0], (String) r[1], (Long) r[2]))
                .sorted((a, b) -> Long.compare(b.votes(), a.votes()))
                .collect(Collectors.toList());

        Set<UUID> assignedCandidates = new HashSet<>();
        Set<String> filledPositions = new HashSet<>();
        Map<String, CandidateResultDTO> winnerByPosition = new LinkedHashMap<>();

        for (String position : positions) {
            winnerByPosition.put(position, null);
        }

        boolean anonymize = false;
        int anonIndex = 1;

        for (Pairing p : pairings) {
            if (assignedCandidates.contains(p.candidateId())) continue;
            if (filledPositions.contains(p.position())) continue;

            Candidate c = candidateById.get(p.candidateId());
            if (c == null) continue;

            CandidateResultDTO dto = anonymize
                    ? new CandidateResultDTO(c.getId(), "Candidate " + anonIndex++, null, "Anonymous Partylist", p.votes())
                    : new CandidateResultDTO(c.getId(), c.getFirstName() + " " + c.getLastName(),
                    c.getPhotoImageUrl(),
                    c.getPartylistId() != null ? partylistNamesById.get(c.getPartylistId()) : null,
                    p.votes());

            winnerByPosition.put(p.position(), dto);
            assignedCandidates.add(p.candidateId());
            filledPositions.add(p.position());
        }

        Map<String, List<CandidateResultDTO>> result = new LinkedHashMap<>();
        for (Map.Entry<String, CandidateResultDTO> e : winnerByPosition.entrySet()) {
            if (e.getValue() != null) {
                result.put(e.getKey(), new ArrayList<>(List.of(e.getValue())));
            }
        }
        return result;
    }

    private Map<UUID, String> resolvePartylistNames(List<Candidate> candidates) {
        List<UUID> partylistIds = candidates.stream()
                .map(Candidate::getPartylistId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (partylistIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return partylistRepository.findAllById(partylistIds).stream()
                .collect(Collectors.toMap(Partylist::getId, Partylist::getName));
    }

    @Transactional(readOnly = true)
    public List<EntityResultDTO> getSscLiveResultsForCampus(UUID campusId) {
        return getSscLiveResults().stream()
                .filter(dto -> campusId.toString().equals(dto.getCampusId()))
                .collect(Collectors.toList());
    }

    private boolean hasVotes(ElectionPhase phase) {
        return phase != ElectionPhase.UPCOMING && phase != ElectionPhase.UNSCHEDULED;
    }

    private void applyElectionMeta(EntityResultDTO dto, Election election, DrawDetectionService.DrawOutcome draw) {
        dto.setSchoolYear(election.getSchoolYear());
        dto.setDrawElection(election.isDrawElection());
        dto.setParentElectionId(election.getParentElectionId() != null
                ? election.getParentElectionId().toString() : null);
        if (draw.hasDraw()) {
            dto.setDrawPositions(new ArrayList<>(draw.positions()));
            dto.setDrawMessage(DrawDetectionService.DRAW_MESSAGE);
        }
    }

    private CandidateResultDTO toDto(Candidate c, long votes, Map<UUID, String> partylistNamesById) {
        return new CandidateResultDTO(c.getId(), c.getFirstName() + " " + c.getLastName(),
                c.getPhotoImageUrl(),
                c.getPartylistId() != null ? partylistNamesById.get(c.getPartylistId()) : null,
                votes);
    }
}