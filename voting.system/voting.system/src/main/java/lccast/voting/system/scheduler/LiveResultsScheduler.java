package lccast.voting.system.scheduler;

import lccast.voting.system.event.LiveResultsBroadcaster;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LiveResultsScheduler {

    private final LiveResultsBroadcaster broadcaster;

    public LiveResultsScheduler(LiveResultsBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedRate = 60000)
    public void tick() {
        broadcaster.broadcast();
    }
}