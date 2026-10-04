package lccast.voting.system.service;

import lccast.voting.system.event.VoteCastEvent;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.DrawDetectionService.DrawOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DrawElectionService {

    private static final Logger log = LoggerFactory.getLogger(DrawElectionService.class);
    private static final String DRAW_PREFIX = "Draw - ";
    private static final Duration GRACE = Duration.ofMinutes(1);

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionPartylistRepository electionPartylistRepository;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final PartylistRepository partylistRepository;
    private final DrawDetectionService drawDetectionService;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher eventPublisher;

    public DrawElectionService(ElectionRepository electionRepository,
                               CandidateRepository candidateRepository,
                               ElectionPartylistRepository electionPartylistRepository,
                               ElectionDepartmentRepository electionDepartmentRepository,
                               PartylistRepository partylistRepository,
                               DrawDetectionService drawDetectionService,
                               TransactionTemplate tx,
                               ApplicationEventPublisher eventPublisher) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.electionPartylistRepository = electionPartylistRepository;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.partylistRepository = partylistRepository;
        this.drawDetectionService = drawDetectionService;
        this.tx = tx;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 60_000)
    public void processConcludedElections() {
        List<UUID> due = electionRepository
                .findByStatusAndDrawCheckedAtIsNullAndEndAtBefore(RecordStatus.ACTIVE, Instant.now().minus(GRACE))
                .stream().map(Election::getId).toList();

        for (UUID id : due) {
            try {
                tx.executeWithoutResult(status -> processElection(id));
            } catch (Exception ex) {
                log.error("Draw check failed for election {}", id, ex);
            }
        }
    }

    private void processElection(UUID electionId) {
        Election election = electionRepository.findById(electionId).orElse(null);
        if (election == null
                || election.getStatus() != RecordStatus.ACTIVE
                || election.getDrawCheckedAt() != null) return;

        election.setDrawCheckedAt(Instant.now());

        DrawOutcome outcome = drawDetectionService.detect(election);
        if (outcome.hasDraw()
                && !electionRepository.existsByParentElectionIdAndStatus(electionId, RecordStatus.ACTIVE)) {
            createDrawElection(election, outcome);
        }

        // Published inside the transaction so LiveResultsBroadcaster fires AFTER_COMMIT.
        // Fires even when there is no draw, so live pages flip to CONCLUDED a minute after the end time.
        eventPublisher.publishEvent(new VoteCastEvent(this));
    }

    private Election createDrawElection(Election parent, DrawOutcome outcome) {

        Set<UUID> tiedIds = outcome.tiedByPosition().values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        List<Candidate> tiedCandidates = candidateRepository.findAllById(tiedIds);

        Election draw = new Election();
        draw.setTitle(parent.getTitle().startsWith(DRAW_PREFIX) ? parent.getTitle() : DRAW_PREFIX + parent.getTitle());
        draw.setCategory(parent.getCategory());
        draw.setCampusId(parent.getCampusId());
        draw.setSchoolYear(parent.getSchoolYear());
        draw.setStartAt(null);                         // admin/superadmin sets these after review
        draw.setEndAt(null);
        draw.setStatus(RecordStatus.ACTIVE);
        draw.setActive(true);
        draw.setCreatedBy(parent.getCreatedBy());
        draw.setParentElectionId(parent.getId());
        draw.setDrawPositions(new ArrayList<>(outcome.positions()));
        Election saved = electionRepository.save(draw);   // NOTE: bypasses ElectionSchoolYearGuard on purpose

        for (Candidate src : tiedCandidates) {
            Candidate copy = new Candidate();
            copy.setElectionId(saved.getId());
            copy.setPartylistId(src.getPartylistId());
            copy.setDepartmentId(src.getDepartmentId());
            copy.setStudentId(src.getStudentId());
            copy.setLastName(src.getLastName());
            copy.setFirstName(src.getFirstName());
            copy.setMiddleName(src.getMiddleName());
            copy.setPosition(src.getPosition());
            copy.setCampaignImageUrl(src.getCampaignImageUrl());
            copy.setBackgroundImageUrl(src.getBackgroundImageUrl());
            copy.setPhotoImageUrl(src.getPhotoImageUrl());
            candidateRepository.save(copy);
        }

        if (parent.getCategory() == ElectionCategory.SSC) {
            // only the partylists that still have someone in the tie-break
            tiedCandidates.stream().map(Candidate::getPartylistId)
                    .filter(Objects::nonNull).distinct()
                    .forEach(pid -> partylistRepository.findById(pid).ifPresent(p -> {
                        ElectionPartylist link = new ElectionPartylist();
                        link.setElection(saved);
                        link.setPartylist(p);
                        electionPartylistRepository.save(link);
                    }));
        } else {
            // keep ALL parent departments linked: they decide who is eligible to vote
            for (ElectionDepartment src : electionDepartmentRepository.findByElectionId(parent.getId())) {
                ElectionDepartment link = new ElectionDepartment();
                link.setElection(saved);
                link.setDepartment(src.getDepartment());
                electionDepartmentRepository.save(link);
            }
        }
        return saved;
    }
}