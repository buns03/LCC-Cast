package lccast.voting.system.event;

import lccast.voting.system.service.AnalyticsService;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsPushListener {

    private final AnalyticsService analyticsService;
    private final SimpMessagingTemplate messagingTemplate;

    public AnalyticsPushListener(AnalyticsService analyticsService,
                                 SimpMessagingTemplate messagingTemplate) {
        this.analyticsService = analyticsService;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteCast(VoteCastEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/analytics",
                analyticsService.getFullAnalytics()
        );
    }
}