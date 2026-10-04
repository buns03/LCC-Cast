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
        List<EntityResultDTO> ssc = liveResultsService.getSscLiveResults();
        List<EntityResultDTO> departments = liveResultsService.getDepartmentLiveResults();

        messagingTemplate.convertAndSend("/topic/live-results",
                (Object) Map.of("ssc", ssc, "departments", departments));

        broadcastDepartmentScoped(departments);
    }

    private void broadcastDepartmentScoped(List<EntityResultDTO> departments) {
        for (EntityResultDTO dept : departments) {
            if (dept.getDepartmentIds() == null || dept.getDepartmentIds().isEmpty()) continue;

            for (String departmentId : dept.getDepartmentIds()) {
                messagingTemplate.convertAndSend(
                        "/topic/live-results/department/" + dept.getCampusId() + "/" + departmentId, dept);
            }
        }
    }
}