package lccast.voting.system.event;

import lccast.voting.system.dto.EntityResultDTO;
import lccast.voting.system.service.LiveResultsService;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LiveResultsBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final LiveResultsService liveResultsService;

    public LiveResultsBroadcaster(
            SimpMessagingTemplate messagingTemplate,
            LiveResultsService liveResultsService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.liveResultsService = liveResultsService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteCast(VoteCastEvent event) {
        broadcast();
    }

    public void broadcast() {
        Map<String, Object> payload = Map.of(
                "ssc", liveResultsService.getSscLiveResults(),
                "departments", liveResultsService.getDepartmentLiveResults()
        );
        messagingTemplate.convertAndSend("/topic/live-results", (Object) payload);

        broadcastDepartmentScoped();
    }

    private void broadcastDepartmentScoped() {
        List<EntityResultDTO> departments = liveResultsService.getDepartmentLiveResults();

        for (EntityResultDTO dept : departments) {
            if (dept.getDepartmentIds() == null || dept.getDepartmentIds().isEmpty()) continue;

            for (String departmentId : dept.getDepartmentIds()) {
                String topic = "/topic/live-results/department/"
                        + dept.getCampusId() + "/" + departmentId;

                messagingTemplate.convertAndSend(topic, dept);
            }
        }
    }
}