package lccast.voting.system.service.superadmin;

import lccast.voting.system.model.ElectionEmailCampaign;
import lccast.voting.system.repository.ElectionEmailCampaignRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ElectionEmailScheduler {

    private final ElectionEmailCampaignRepository campaignRepository;
    private final ElectionEmailService electionEmailService;

    public ElectionEmailScheduler(
            ElectionEmailCampaignRepository campaignRepository,
            ElectionEmailService electionEmailService
    ) {
        this.campaignRepository = campaignRepository;
        this.electionEmailService = electionEmailService;
    }

    // Checks every 60 seconds for due scheduled emails
    @Scheduled(fixedRate = 60000)
    public void dispatchDueCampaigns() {
        List<ElectionEmailCampaign> due = campaignRepository
                .findByStatusAndScheduledAtLessThanEqual("SCHEDULED", Instant.now());

        for (ElectionEmailCampaign campaign : due) {
            electionEmailService.sendScheduledCampaign(campaign);
        }
    }
}