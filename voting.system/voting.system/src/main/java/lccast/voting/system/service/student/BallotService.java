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

import lccast.voting.system.service.DrawDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private static final Logger log = LoggerFactory.getLogger(BallotService.class);
    private final DrawDetectionService drawDetectionService;

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
            SimpMessagingTemplate messagingTemplate,      // ADD
            DrawDetectionService drawDetectionService
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
        this.drawDetectionService = drawDetectionService;
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

        Department department = electionDepartments.get(0);
        boolean representative = department.getVotingType() == VotingType.REPRESENTATIVE;

        if (votes == null || votes.isEmpty()) {
            throw new IllegalArgumentException("No votes submitted.");
        }

        // Candidates this voter is allowed to vote for
        Map<UUID, Candidate> candidatesById = new HashMap<>();
        for (Department dept : electionDepartments) {
            for (Candidate c : candidateRepository.findByElectionIdAndDepartmentId(electionId, dept.getId())) {
                candidatesById.put(c.getId(), c);
            }
        }

        // Positions actually open (matters for draw/tie re-run elections)
        Set<String> allowedPositions =
                new HashSet<>(drawDetectionService.openPositions(election, department));

        Set<String> seenPositions = new HashSet<>();
        Set<UUID> seenCandidates = new HashSet<>();
        List<VoteItem> accepted = new ArrayList<>();

        for (VoteItem vote : votes) {
            if (vote.position == null || vote.position.isBlank()) {
                throw new IllegalArgumentException("Vote item missing position.");
            }
            if (!seenPositions.add(vote.position)) {
                throw new IllegalArgumentException("Duplicate position in submission: " + vote.position);
            }
            if (vote.skipped || vote.candidateId == null) {
                continue;
            }

            Candidate candidate = candidatesById.get(vote.candidateId);
            if (candidate == null) {
                throw new IllegalArgumentException("Invalid candidate selection.");
            }
            if (!seenCandidates.add(candidate.getId())) {
                throw new IllegalArgumentException("A candidate can only be selected once.");
            }
            if (!allowedPositions.contains(vote.position)) {
                throw new IllegalArgumentException("Position is not open for voting: " + vote.position);
            }
            if (!representative && !vote.position.equals(candidate.getPosition())) {
                throw new IllegalArgumentException("Candidate does not match the declared position.");
            }
            accepted.add(vote);
        }

        Instant now = Instant.now();

        Ballot ballot = new Ballot();
        ballot.setElection(election);
        ballot.setVoter(voter);
        ballot.setSubmittedAt(now);
        ballot.setCreatedAt(now);

        Ballot savedBallot;
        try {
            // Unique (election_id, voter_id) in the DB is the real duplicate guard
            savedBallot = ballotRepository.saveAndFlush(ballot);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException("You have already voted in this election.");
        }

        List<BallotVote> rows = new ArrayList<>();
        for (VoteItem vote : accepted) {
            BallotVote ballotVote = new BallotVote();
            ballotVote.setBallot(savedBallot);
            ballotVote.setCandidate(candidatesById.get(vote.candidateId));
            ballotVote.setPosition(representative ? vote.position : null);
            ballotVote.setCreatedAt(now);
            rows.add(ballotVote);
        }
        ballotVoteRepository.saveAll(rows);

        voter.setVotingStatus(VotingStatus.VOTED);
        voter.setTimeVoted(now);
        voterRepository.save(voter);

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
                    newVoteLog.setVotedAt(now);
                    newVoteLog.setElectionName(election.getTitle());
                    newVoteLog.setElectionCategory(
                            election.getCategory() != null ? election.getCategory().name() : null
                    );
                    newVoteLog.setCampusName(
                            campusRepository.findById(voter.getCampusId())
                                    .map(Campus::getName)
                                    .orElse(null)
                    );

                    return voteLogRepository.save(newVoteLog);
                });

        notifyAfterCommit(voteLog.getId().toString());

        Map<String, Object> result = new HashMap<>();
        result.put("referenceNumber", savedBallot.getId().toString());
        result.put("votedAt", savedBallot.getSubmittedAt().toString());
        return result;
    }

    /** Runs only after the DB commit succeeds, and never fails the voter's request. */
    private void notifyAfterCommit(String voteLogId) {
        Runnable task = () -> {
            try {
                messagingTemplate.convertAndSend(
                        "/topic/history/vote-logs",
                        HistoryEventDTO.of("VOTE_CAST", voteLogId)
                );
                eventPublisher.publishEvent(new VoteCastEvent(BallotService.this));
            } catch (Exception e) {
                log.warn("Post-commit vote notification failed", e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}