package lccast.voting.system.service.student;

import lccast.voting.system.dto.HistoryEventDTO;
import lccast.voting.system.dto.student.VoteItemRequest;
import lccast.voting.system.dto.student.VoteSubmissionRequest;
import lccast.voting.system.dto.student.VoteSubmissionResponse;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import lccast.voting.system.event.VoteCastEvent;
import lccast.voting.system.model.Campus;
import lccast.voting.system.repository.CampusRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VoteSubmissionService {

    private final SscElectionService sscElectionService;
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final BallotRepository ballotRepository;
    private final BallotVoteRepository ballotVoteRepository;
    private final VoteLogRepository voteLogRepository;
    private final VoterRepository voterRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CampusRepository campusRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public VoteSubmissionService(
            SscElectionService sscElectionService,
            ElectionRepository electionRepository,
            CandidateRepository candidateRepository,
            BallotRepository ballotRepository,
            BallotVoteRepository ballotVoteRepository,
            VoteLogRepository voteLogRepository,
            VoterRepository voterRepository,
            ApplicationEventPublisher eventPublisher,
            CampusRepository campusRepository,         // ADD
            SimpMessagingTemplate messagingTemplate     // ADD
    ) {
        this.sscElectionService = sscElectionService;
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.ballotRepository = ballotRepository;
        this.ballotVoteRepository = ballotVoteRepository;
        this.voteLogRepository = voteLogRepository;
        this.voterRepository = voterRepository;
        this.eventPublisher = eventPublisher;
        this.campusRepository = campusRepository;       // ADD
        this.messagingTemplate = messagingTemplate;      // ADD
    }

    @Transactional
    public VoteSubmissionResponse submitSscVote(
            Voter voter,
            VoteSubmissionRequest request
    ) {

        // =====================================================
        // BASIC REQUEST VALIDATION
        // =====================================================

        if (request.getElectionId() == null) {
            throw new IllegalArgumentException("Election id is required.");
        }

        if (request.getVotes() == null || request.getVotes().isEmpty()) {
            throw new IllegalArgumentException("No votes submitted.");
        }

        Election election = electionRepository.findById(request.getElectionId())
                .orElseThrow(() -> new IllegalArgumentException("Election not found."));

        if (election.getCategory() != ElectionCategory.SSC) {
            throw new IllegalArgumentException("This endpoint only accepts SSC election votes.");
        }

        if (!election.getCampusId().equals(voter.getCampusId())) {
            throw new IllegalStateException("This election does not belong to your campus.");
        }

        // =====================================================
        // PHASE CHECK — voting only allowed while ONGOING
        // =====================================================

        ElectionPhase phase = sscElectionService.computePhase(election);

        if (phase != ElectionPhase.ONGOING) {
            throw new IllegalStateException("Voting is not currently open for this election.");
        }

        // =====================================================
        // DUPLICATE VOTE CHECK
        // =====================================================

        boolean alreadyVoted = ballotRepository.existsByElection_IdAndVoter_Id(
                election.getId(),
                voter.getId()
        );

        if (alreadyVoted) {
            throw new IllegalStateException("You have already voted in this election.");
        }

        // =====================================================
        // VALIDATE EACH SELECTED CANDIDATE
        // =====================================================

        List<Candidate> validatedCandidates = new java.util.ArrayList<>();
        java.util.Set<String> seenPositions = new java.util.HashSet<>();

        for (VoteItemRequest item : request.getVotes()) {

            if (item.getPosition() == null || item.getPosition().isBlank()) {
                throw new IllegalArgumentException("Vote item missing position.");
            }

            if (!seenPositions.add(item.getPosition())) {
                throw new IllegalArgumentException(
                        "Duplicate position in submission: " + item.getPosition()
                );
            }

            if (item.isSkipped() || item.getCandidateId() == null) {
                continue; // skipped position — nothing to record
            }

            Candidate candidate = candidateRepository.findById(item.getCandidateId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Candidate not found: " + item.getCandidateId()
                    ));

            if (!candidate.getElectionId().equals(election.getId())) {
                throw new IllegalArgumentException(
                        "Candidate does not belong to this election."
                );
            }

            if (!candidate.getPosition().equals(item.getPosition())) {
                throw new IllegalArgumentException(
                        "Candidate does not match the declared position."
                );
            }

            validatedCandidates.add(candidate);
        }

        // =====================================================
        // CREATE BALLOT
        // =====================================================

        Instant now = Instant.now();

        Ballot ballot = new Ballot();
        ballot.setElection(election);
        ballot.setVoter(voter);
        ballot.setSubmittedAt(now);
        ballot.setCreatedAt(now);

        Ballot savedBallot;

        try {
            savedBallot = ballotRepository.saveAndFlush(ballot);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException("You have already voted in this election.");
        }

        // =====================================================
        // CREATE BALLOT VOTES (actual selections — anonymous
        // from the admin's perspective; only reachable via
        // ballot -> voter, which admin-facing code must never join)
        // =====================================================

        for (Candidate candidate : validatedCandidates) {
            BallotVote ballotVote = new BallotVote();
            ballotVote.setBallot(savedBallot);
            ballotVote.setCandidate(candidate);
            ballotVote.setCreatedAt(now);
            ballotVoteRepository.save(ballotVote);
        }

        // =====================================================
        // AUDIT — records THAT the voter voted, not WHAT for
        // =====================================================

        VoteLog voteLog = voteLogRepository
                .findByBallotId(savedBallot.getId())
                .orElseGet(() -> {
                    VoteLog newVoteLog = new VoteLog();

                    newVoteLog.setElectionId(election.getId());
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
                    newVoteLog.setElectionCategory(election.getCategory().name());
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

        // =====================================================
        // UPDATE VOTER STATUS
        // =====================================================

        voter.setVotingStatus(VotingStatus.VOTED);
        voter.setTimeVoted(now);
        voter.setUpdatedAt(now);

        voterRepository.save(voter);

        // =====================================================
        // RESPONSE
        // =====================================================

        VoteSubmissionResponse response = new VoteSubmissionResponse();
        response.setBallotId(savedBallot.getId());
        response.setVotedAt(now);
        response.setMessage("Your vote has been successfully recorded.");

        eventPublisher.publishEvent(new VoteCastEvent(this));

        return response;
    }
}