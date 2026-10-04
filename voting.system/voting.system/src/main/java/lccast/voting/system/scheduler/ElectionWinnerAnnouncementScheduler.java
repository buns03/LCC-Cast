package lccast.voting.system.scheduler;

import lccast.voting.system.model.Election;
import lccast.voting.system.model.RecordStatus;
import lccast.voting.system.repository.ElectionRepository;
import lccast.voting.system.service.superadmin.ElectionEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ElectionWinnerAnnouncementScheduler {

    private static final Logger log = LoggerFactory.getLogger(ElectionWinnerAnnouncementScheduler.class);

    private final ElectionRepository electionRepository;
    private final ElectionEmailService electionEmailService;

    public ElectionWinnerAnnouncementScheduler(
            ElectionRepository electionRepository,
            ElectionEmailService electionEmailService
    ) {
        this.electionRepository = electionRepository;
        this.electionEmailService = electionEmailService;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void sendDueWinnerAnnouncements() {
        List<Election> active = electionRepository.findByStatus(RecordStatus.ACTIVE);
        Instant now = Instant.now();

        log.debug("Winner announcement sweep — checking {} active elections", active.size());

        for (Election election : active) {
            if (election.getEndAt() == null) {
                if (!election.isDrawElection()) {
                    log.warn("Election {} has no endAt — skipping winner sweep for it.", election.getId());
                }
                continue;
            }

            if (!now.isAfter(election.getEndAt())) continue;

            try {
                electionEmailService.sendWinnerAnnouncement(election.getId(), null);
                log.info("Sent winner announcement for electionId={}", election.getId());
            } catch (IllegalStateException alreadySent) {
                log.debug("Winner announcement already sent for electionId={}", election.getId());
            } catch (Exception e) {
                log.error("Failed to send winner announcement for electionId={}", election.getId(), e);
            }
        }
    }
}