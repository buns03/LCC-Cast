package lccast.voting.system.controller.student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.student.BallotService;
import lccast.voting.system.service.superadmin.ElectionService;
import lccast.voting.system.service.student.VoterDepartmentElectionService;
import java.util.stream.Collectors;

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

    public DepartmentElectionApiController(
            VoterDepartmentElectionService voterDepartmentElectionService,
            ElectionService electionService,
            CandidateRepository candidateRepository,
            VoterRepository voterRepository,
            BallotRepository ballotRepository,
            BallotService ballotService,
            ElectionDepartmentRepository electionDepartmentRepository
    ) {
        this.voterDepartmentElectionService = voterDepartmentElectionService;
        this.electionService = electionService;
        this.candidateRepository = candidateRepository;
        this.voterRepository = voterRepository;
        this.ballotRepository = ballotRepository;
        this.ballotService = ballotService;
        this.electionDepartmentRepository = electionDepartmentRepository;
    }

    @GetMapping("/current")
    public Map<String, Object> getCurrent(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session == null || session.getAttribute("userId") == null) {
            response.put("status", "UNAUTHENTICATED");
            return response;
        }

        UUID authUserId = UUID.fromString(session.getAttribute("userId").toString());
        Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);

        if (voter == null) {
            response.put("status", "NOT_A_VOTER");
            return response;
        }

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

        // Only the department rows (partylists) actually linked to THIS election.
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
        response.put("department", primaryDepartment.getName()); // shared program name, e.g. "BSIS"
        response.put("campus", primaryDepartment.getCampus().getName());
        response.put("votingType", primaryDepartment.getVotingType().name());
        response.put("positions", primaryDepartment.getPositions());

        Map<UUID, String> partylistNamesByDepartmentId = electionDepartments.stream()
                .collect(Collectors.toMap(Department::getId, Department::getTitle));

        List<Map<String, Object>> candidates = new ArrayList<>();

        for (Department dept : electionDepartments) {
            List<Candidate> deptCandidates =
                    candidateRepository.findByElectionIdAndDepartmentId(election.getId(), dept.getId());

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
            }
        }

        response.put("candidates", candidates);

        boolean hasVoted = ballotRepository.existsByElection_IdAndVoter_Id(election.getId(), voter.getId());
        response.put("hasVoted", hasVoted);

        return response;
    }

    @PostMapping("/vote")
    public Map<String, Object> submitVote(
            @RequestBody VoteRequest voteRequest,
            HttpServletRequest request
    ) {
        Map<String, Object> response = new HashMap<>();

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
                    request,
                    voteRequest.electionId,
                    items
            );

            response.putAll(result);
            return response;

        } catch (IllegalStateException | IllegalArgumentException e) {
            response.put("message", e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
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