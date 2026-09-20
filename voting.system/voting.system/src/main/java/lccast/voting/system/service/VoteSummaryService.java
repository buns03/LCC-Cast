package lccast.voting.system.service;

import lccast.voting.system.dto.CandidateVoteDTO;
import lccast.voting.system.dto.VoteSummaryDTO;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.BallotRepository;
import lccast.voting.system.repository.BallotVoteRepository;
import lccast.voting.system.repository.DepartmentRepository;
import lccast.voting.system.repository.PartylistRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VoteSummaryService {
        private final BallotRepository ballotRepository;
        private final BallotVoteRepository ballotVoteRepository;
        private final DepartmentRepository departmentRepository;
        private final PartylistRepository partylistRepository;

        public VoteSummaryService(
                BallotRepository ballotRepository,
                BallotVoteRepository ballotVoteRepository,
                DepartmentRepository departmentRepository,
                PartylistRepository partylistRepository) {
            this.ballotRepository = ballotRepository;
            this.ballotVoteRepository = ballotVoteRepository;
            this.departmentRepository = departmentRepository;
            this.partylistRepository = partylistRepository;
        }


        // VOTER-ONLY: caller must pass the id of the currently authenticated
        // session voter. Never expose this to admin-facing code paths.
        public List<VoteSummaryDTO> getVoteSummariesForVoter(UUID voterId) {

            List<Ballot> ballots =
                    ballotRepository.findByVoter_IdOrderBySubmittedAtDesc(voterId);

            List<VoteSummaryDTO> summaries = new ArrayList<>();

            for (Ballot ballot : ballots) {
                summaries.add(toSummaryDTO(ballot));
            }

            return summaries;
        }

        private VoteSummaryDTO toSummaryDTO(Ballot ballot) {

            Election election = ballot.getElection();

            List<BallotVote> votes =
                    ballotVoteRepository.findByBallot_Id(ballot.getId());

            List<CandidateVoteDTO> candidateDTOs = new ArrayList<>();

            String votingType = null;
            String departmentName = null;

            for (BallotVote vote : votes) {
                Candidate candidate = vote.getCandidate();

                String affiliationName = null;

                if (candidate.getPartylistId() != null) {
                    affiliationName = partylistRepository.findById(candidate.getPartylistId())
                            .map(Partylist::getName)
                            .orElse("Independent");
                } else if (candidate.getDepartmentId() != null) {
                    Department dept = departmentRepository.findById(candidate.getDepartmentId())
                            .orElse(null);
                    if (dept != null) {
                        // set once per ballot — same for every candidate on a
                        // department ballot
                        departmentName = dept.getName();
                        votingType = dept.getVotingType() != null
                                ? dept.getVotingType().name()
                                : null;
                        affiliationName = dept.getName();
                    }
                }

                candidateDTOs.add(new CandidateVoteDTO(
                        vote.getPosition() != null ? vote.getPosition() : candidate.getPosition(),
                        buildCandidateName(candidate),
                        affiliationName,
                        buildImageUrl(candidate.getPhotoImageUrl())   // <-- updated
                ));
            }

            boolean isSsc = election.getCategory() == ElectionCategory.SSC;

            return new VoteSummaryDTO(
                    ballot.getId().toString(),
                    isSsc ? "ssc" : "department",
                    isSsc ? null : votingType,
                    election.getTitle(),
                    isSsc ? "SSC Election" : "Department Election",
                    null, // campus — filled in by controller from session, see Step 4 note
                    isSsc ? null : departmentName,
                    ballot.getSubmittedAt(),
                    candidateDTOs
            );
        }

        private String buildCandidateName(Candidate c) {
            StringBuilder sb = new StringBuilder();
            sb.append(c.getFirstName());
            if (c.getMiddleName() != null && !c.getMiddleName().isBlank()) {
                sb.append(" ").append(c.getMiddleName());
            }
            sb.append(" ").append(c.getLastName());
            return sb.toString();
        }

    // ADD THIS NEW METHOD RIGHT AFTER buildCandidateName:
    private String buildImageUrl(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }
        return "/files/" + storagePath;
    }

}