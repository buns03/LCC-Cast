package lccast.voting.system.controller.student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.DrawDetectionService;
import lccast.voting.system.service.student.BallotService;
import lccast.voting.system.service.superadmin.ElectionService;
import lccast.voting.system.service.student.VoterDepartmentElectionService;
import java.util.stream.Collectors;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/voter/api/department-elections")
public class DepartmentElectionApiController {

    private final VoterDepartmentElectionService voterDepartmentElectionService;
    private final ElectionService electionService;
    private final CandidateRepository candidateRepository;
    private final VoterRepository voterRepository;
    private final BallotRepository ballotRepository;
    private final BallotService ballotService;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final DrawDetectionService drawDetectionService;
    private final Cache<String, Map<String, Object>> payloadCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .maximumSize(500)
            .build();

    public DepartmentElectionApiController(
            VoterDepartmentElectionService voterDepartmentElectionService,
            ElectionService electionService,
            CandidateRepository candidateRepository,
            VoterRepository voterRepository,
            BallotRepository ballotRepository,
            BallotService ballotService,
            ElectionDepartmentRepository electionDepartmentRepository,
            DrawDetectionService drawDetectionService
    ) {
        this.voterDepartmentElectionService = voterDepartmentElectionService;
        this.electionService = electionService;
        this.candidateRepository = candidateRepository;
        this.voterRepository = voterRepository;
        this.ballotRepository = ballotRepository;
        this.ballotService = ballotService;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.drawDetectionService = drawDetectionService;
    }

    @GetMapping("/current")
    public Map<String, Object> getCurrent(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "UNAUTHENTICATED");
            return response;
        }

        UUID authUserId = UUID.fromString(session.getAttribute("userId").toString());
        Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);

        if (voter == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "NOT_A_VOTER");
            return response;
        }

        String cacheKey = voter.getCampusId() + ":" + String.valueOf(voter.getProgramCourse()).toLowerCase();
        Map<String, Object> base = payloadCache.get(cacheKey, k -> buildBasePayload(voter));

        Map<String, Object> response = new HashMap<>(base);

        Object electionId = base.get("electionId");
        if (electionId instanceof UUID id) {
            response.put("hasVoted", ballotRepository.existsByElection_IdAndVoter_Id(id, voter.getId()));
        }

        return response;
    }

    private Map<String, Object> buildBasePayload(Voter voter) {
        Map<String, Object> response = new HashMap<>();

        List<Department> voterDepartments = voterDepartmentElectionService.resolveVoterDepartments(voter);

        if (voterDepartments.isEmpty()) {
            response.put("status", "NO_DEPARTMENT_MATCH");
            return response;
        }

        Optional<Election> electionOpt = voterDepartmentElectionService.resolveVisibleElection(voter);

        if (electionOpt.isEmpty()) {
            response.put("status", "NO_ELECTION");
            return response;
        }

        Election election = electionOpt.get();

        Set<UUID> linkedDepartmentIds = electionDepartmentRepository
                .findByElectionId(election.getId())
                .stream()
                .map(link -> link.getDepartment().getId())
                .collect(Collectors.toSet());

        List<Department> electionDepartments = voterDepartments.stream()
                .filter(d -> linkedDepartmentIds.contains(d.getId()))
                .toList();

        if (electionDepartments.isEmpty()) {
            response.put("status", "NO_DEPARTMENT_MATCH");
            return response;
        }

        Department primaryDepartment = electionDepartments.get(0);

        ElectionService.VoterElectionPhase phase = electionService.calculateVoterPhase(election);

        response.put("status", phase.name());
        response.put("electionId", election.getId());
        response.put("electionName", election.getTitle());
        response.put("schoolYear", election.getSchoolYear());
        response.put("scheduledStartAt", election.getStartAt());
        response.put("scheduledEndAt", election.getEndAt());
        response.put("department", primaryDepartment.getName());
        response.put("campus", primaryDepartment.getCampus().getName());
        response.put("votingType", primaryDepartment.getVotingType().name());
        response.put("positions", drawDetectionService.openPositions(election, primaryDepartment));
        response.put("isDrawElection", election.isDrawElection());

        Map<UUID, String> partylistNamesByDepartmentId = electionDepartments.stream()
                .collect(Collectors.toMap(Department::getId, Department::getTitle));

        List<Map<String, Object>> candidates = new ArrayList<>();
        List<Map<String, Object>> partylists = new ArrayList<>();

        for (Department dept : electionDepartments) {
            List<Candidate> deptCandidates =
                    candidateRepository.findByElectionIdAndDepartmentId(election.getId(), dept.getId());

            List<Map<String, Object>> partylistMembers = new ArrayList<>();

            for (Candidate c : deptCandidates) {
                Map<String, Object> candidateMap = new HashMap<>();
                candidateMap.put("id", c.getId());
                candidateMap.put("firstName", c.getFirstName());
                candidateMap.put("middleName", c.getMiddleName());
                candidateMap.put("lastName", c.getLastName());
                candidateMap.put("position", c.getPosition());
                candidateMap.put("photoImageUrl", c.getPhotoImageUrl());
                candidateMap.put("campaignImageUrl", c.getCampaignImageUrl());
                candidateMap.put("backgroundImageUrl", c.getBackgroundImageUrl());
                candidateMap.put("departmentId", dept.getId());
                candidateMap.put("partylistName", partylistNamesByDepartmentId.get(dept.getId()));
                candidates.add(candidateMap);

                Map<String, Object> memberMap = new HashMap<>();
                memberMap.put("position", c.getPosition());
                memberMap.put("name", buildCandidateFullName(c));
                memberMap.put("photoImageUrl", c.getPhotoImageUrl());
                memberMap.put("campaignImageUrl", c.getCampaignImageUrl());
                memberMap.put("backgroundImageUrl", c.getBackgroundImageUrl());
                partylistMembers.add(memberMap);
            }

            Map<String, Object> partylistMap = new HashMap<>();
            partylistMap.put("id", dept.getId());
            partylistMap.put("name", dept.getTitle());
            partylistMap.put("posterImageUrl", dept.getPosterImageUrl());
            partylistMap.put("logoImageUrl", dept.getPosterLogoUrl());
            partylistMap.put("members", partylistMembers);
            partylists.add(partylistMap);
        }

        response.put("candidates", candidates);
        response.put("partylists", partylists);

        return response;
    }

    private String buildCandidateFullName(Candidate candidate) {
        String middle = candidate.getMiddleName();

        String fullName = candidate.getFirstName()
                + (middle != null && !middle.isBlank() ? " " + middle : "")
                + " " + candidate.getLastName();

        return fullName.trim();
    }

//    @PostMapping("/vote")
//    public Map<String, Object> submitVote(
//            @RequestBody VoteRequest voteRequest,
//            HttpServletRequest request
//    ) {
//        Map<String, Object> response = new HashMap<>();
//
//        try {
//            List<BallotService.VoteItem> items = new ArrayList<>();
//
//            if (voteRequest.votes != null) {
//                for (VoteRequest.VoteEntry entry : voteRequest.votes) {
//                    BallotService.VoteItem item = new BallotService.VoteItem();
//                    item.positionId = entry.positionId;
//                    item.position = entry.position;
//                    item.candidateId = entry.candidateId;
//                    item.skipped = entry.skipped;
//                    items.add(item);
//                }
//            }
//
//            Map<String, Object> result = ballotService.submitDepartmentVote(
//                    request,
//                    voteRequest.electionId,
//                    items
//            );
//
//            response.putAll(result);
//            return response;
//
//        } catch (IllegalStateException | IllegalArgumentException e) {
//            response.put("message", e.getMessage());
//            throw new org.springframework.web.server.ResponseStatusException(
//                    org.springframework.http.HttpStatus.BAD_REQUEST,
//                    e.getMessage()
//            );
//        }
//    }

    @PostMapping("/vote")
    public ResponseEntity<Map<String, Object>> submitVote(
            @RequestBody VoteRequest voteRequest,
            HttpServletRequest request
    ) {
        try {
            List<BallotService.VoteItem> items = new ArrayList<>();

            if (voteRequest.votes != null) {
                for (VoteRequest.VoteEntry entry : voteRequest.votes) {
                    BallotService.VoteItem item = new BallotService.VoteItem();
                    item.positionId = entry.positionId;
                    item.position = entry.position;
                    item.candidateId = entry.candidateId;
                    item.skipped = entry.skipped;
                    items.add(item);
                }
            }

            Map<String, Object> result = ballotService.submitDepartmentVote(
                    request, voteRequest.electionId, items);

            return ResponseEntity.ok(new HashMap<>(result));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    public static class VoteRequest {
        public UUID electionId;
        public List<VoteEntry> votes;

        public static class VoteEntry {
            public String positionId;
            public String position;
            public UUID candidateId;
            public boolean skipped;
        }
    }
}