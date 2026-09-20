package lccast.voting.system.service.student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import lccast.voting.system.dto.HistoryEventDTO;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.superadmin.ElectionService;
import org.springframework.context.ApplicationEventPublisher;
import lccast.voting.system.event.VoteCastEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lccast.voting.system.repository.CampusRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class BallotService {

    private final VoterRepository voterRepository;
    private final ElectionRepository electionRepository;
    private final ElectionService electionService;
    private final VoterDepartmentElectionService voterDepartmentElectionService;
    private final CandidateRepository candidateRepository;
    private final BallotRepository ballotRepository;
    private final BallotVoteRepository ballotVoteRepository;
    private final VoteLogRepository voteLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CampusRepository campusRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public BallotService(
            VoterRepository voterRepository,
            ElectionRepository electionRepository,
            ElectionService electionService,
            VoterDepartmentElectionService voterDepartmentElectionService,
            CandidateRepository candidateRepository,
            BallotRepository ballotRepository,
            BallotVoteRepository ballotVoteRepository,
            VoteLogRepository voteLogRepository,
            ApplicationEventPublisher eventPublisher,
            CampusRepository campusRepository,          // ADD
            SimpMessagingTemplate messagingTemplate      // ADD
    ) {
        this.voterRepository = voterRepository;
        this.electionRepository = electionRepository;
        this.electionService = electionService;
        this.voterDepartmentElectionService = voterDepartmentElectionService;
        this.candidateRepository = candidateRepository;
        this.ballotRepository = ballotRepository;
        this.ballotVoteRepository = ballotVoteRepository;
        this.voteLogRepository = voteLogRepository;
        this.eventPublisher = eventPublisher;
        this.campusRepository = campusRepository;        // ADD
        this.messagingTemplate = messagingTemplate;       // ADD
    }

    public static class VoteItem {
        public String positionId;
        public String position;
        public UUID candidateId;
        public boolean skipped;
    }

    @Transactional
    public Map<String, Object> submitDepartmentVote(
            HttpServletRequest request,
            UUID electionId,
            List<VoteItem> votes
    ) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            throw new IllegalStateException("Not authenticated.");
        }

        UUID authUserId = UUID.fromString(session.getAttribute("userId").toString());

        Voter voter = voterRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new IllegalStateException("Voter not found."));

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new IllegalArgumentException("Election not found."));

        ElectionService.VoterElectionPhase phase = electionService.calculateVoterPhase(election);

        if (phase != ElectionService.VoterElectionPhase.ONGOING) {
            throw new IllegalStateException("Voting is not currently open for this election.");
        }

        List<Department> electionDepartments =
                voterDepartmentElectionService.resolveVoterDepartmentsForElection(voter, electionId);

        if (electionDepartments.isEmpty()) {
            throw new IllegalStateException("You do not belong to a department eligible for this election.");
        }

        Optional<Election> visible = voterDepartmentElectionService.resolveVisibleElection(voter);

        if (visible.isEmpty() || !visible.get().getId().equals(electionId)) {
            throw new IllegalStateException("You are not eligible to vote in this election.");
        }

        if (ballotRepository.existsByElection_IdAndVoter_Id(electionId, voter.getId())) {
            throw new IllegalStateException("You have already voted in this election.");
        }

        Department department = electionDepartments.get(0); // used below only for votingType (shared across all linked departments)

        List<Candidate> validCandidates = new java.util.ArrayList<>();
        for (Department dept : electionDepartments) {
            validCandidates.addAll(
                    candidateRepository.findByElectionIdAndDepartmentId(electionId, dept.getId())
            );
        }

        Set<UUID> validCandidateIds = new HashSet<>();
        for (Candidate c : validCandidates) {
            validCandidateIds.add(c.getId());
        }

        if (votes == null || votes.isEmpty()) {
            throw new IllegalArgumentException("No votes submitted.");
        }

        for (VoteItem vote : votes) {
            if (vote.skipped || vote.candidateId == null) {
                continue;
            }
            if (!validCandidateIds.contains(vote.candidateId)) {
                throw new IllegalArgumentException("Invalid candidate selection.");
            }
        }

        Ballot ballot = new Ballot();
        ballot.setElection(election);
        ballot.setVoter(voter);
        ballot.setSubmittedAt(Instant.now());
        ballot.setCreatedAt(Instant.now());

        Ballot savedBallot;

        try {
            savedBallot = ballotRepository.saveAndFlush(ballot);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException("You have already voted in this election.");
        }

        for (VoteItem vote : votes) {
            if (vote.skipped || vote.candidateId == null) {
                continue;
            }

            Candidate candidate = validCandidates.stream()
                    .filter(c -> c.getId().equals(vote.candidateId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid candidate selection."));

            BallotVote ballotVote = new BallotVote();
            ballotVote.setBallot(savedBallot);
            ballotVote.setCandidate(candidate);
            ballotVote.setPosition(
                    department.getVotingType() == VotingType.REPRESENTATIVE
                            ? vote.position
                            : null
            );
            ballotVote.setCreatedAt(Instant.now());
            ballotVoteRepository.save(ballotVote);
        }

        voter.setVotingStatus(VotingStatus.VOTED);
        voter.setTimeVoted(Instant.now());
        voterRepository.save(voter);

        System.out.println(">>> BallotService.submitDepartmentVote CALLED - election.category=" + election.getCategory());

        VoteLog voteLog = voteLogRepository
                .findByBallotId(savedBallot.getId())
                .orElseGet(() -> {
                    VoteLog newVoteLog = new VoteLog();

                    newVoteLog.setElectionId(electionId);
                    newVoteLog.setVoterId(voter.getId());
                    newVoteLog.setBallotId(savedBallot.getId());
                    newVoteLog.setStudentId(voter.getStudentId());
                    newVoteLog.setFullName(voter.getFullName());
                    newVoteLog.setEmail(voter.getEmail());
                    newVoteLog.setProgramCourse(voter.getProgramCourse());
                    newVoteLog.setYearLevel(voter.getYearLevel());
                    newVoteLog.setCampusId(voter.getCampusId());
                    newVoteLog.setSection(voter.getSection());
                    newVoteLog.setVoteStatus(VotingStatus.VOTED);
                    newVoteLog.setVotedAt(Instant.now());
                    newVoteLog.setElectionName(election.getTitle());
                    newVoteLog.setElectionCategory(
                            election.getCategory() != null
                                    ? election.getCategory().name()
                                    : null
                    );
                    newVoteLog.setCampusName(
                            campusRepository.findById(voter.getCampusId())
                                    .map(Campus::getName)
                                    .orElse(null)
                    );

                    return voteLogRepository.save(newVoteLog);
                });

        messagingTemplate.convertAndSend(
                "/topic/history/vote-logs",
                HistoryEventDTO.of("VOTE_CAST", voteLog.getId().toString())
        );

        Map<String, Object> result = new HashMap<>();
        result.put("referenceNumber", savedBallot.getId().toString());
        result.put("votedAt", savedBallot.getSubmittedAt().toString());

        eventPublisher.publishEvent(new VoteCastEvent(this));

        return result;
    }
}